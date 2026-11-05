const encoder = new TextEncoder();
const MAX_BODY_BYTES = 32_768;
const MAX_SIGNATURE_AGE_SECONDS = 300;
const FIREBASE_TOKEN_URL = "https://oauth2.googleapis.com/token";
const FIREBASE_SCOPE =
  "https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/firebase.database";
const PAYMENT_REFERENCE_PATTERN = /\bOI[A-F0-9]{12}\b/g;

let cachedFirebaseToken = null;

/**
 * Endpoint tiếp nhận SePay Webhook xử lý gạch nợ thanh toán VietQR tự động.
 * - Xác thực HMAC-SHA256 theo từng storeId của quán.
 * - Kiểm tra số tài khoản nhận tiền và mã tham chiếu thanh toán (paymentReference dạng OI...).
 * - Cập nhật trạng thái đơn hàng sang PAID và PENDING trực tiếp trên Firebase Realtime Database.
 */
export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method !== "POST" || url.pathname !== "/webhook") {
      return json({ success: false, message: "Not found" }, 404);
    }

    if (!env.SEPAY_WEBHOOK_SECRETS_JSON || !env.FIREBASE_SERVICE_ACCOUNT_JSON) {
      return json({ success: false, message: "Webhook is not configured" }, 503);
    }

    const storeId = url.searchParams.get("storeId") ?? "";
    if (!/^[A-Za-z0-9_-]{1,128}$/.test(storeId)) {
      return json({ success: false, message: "Invalid store" }, 400);
    }

    let webhookSecrets;
    try {
      webhookSecrets = JSON.parse(env.SEPAY_WEBHOOK_SECRETS_JSON);
    } catch {
      return json({ success: false, message: "Webhook is not configured" }, 503);
    }
    const webhookSecret = webhookSecrets?.[storeId];
    if (typeof webhookSecret !== "string" || !webhookSecret) {
      return json({ success: false, message: "Invalid webhook signature" }, 401);
    }

    const body = await request.text();
    if (encoder.encode(body).byteLength > MAX_BODY_BYTES) {
      return json({ success: false, message: "Request too large" }, 413);
    }

    if (!(await verifySePaySignature(request.headers, body, webhookSecret))) {
      return json({ success: false, message: "Invalid webhook signature" }, 401);
    }

    let transaction;
    try {
      transaction = JSON.parse(body);
    } catch {
      return json({ success: false, message: "Invalid JSON" }, 400);
    }

    if (transaction?.transferType !== "in") {
      return json({ success: true, message: "Ignored non-incoming transaction" });
    }

    const accountNumber = normalizeAccountNumber(transaction?.accountNumber);
    const transactionId = String(transaction?.id ?? "").trim();
    const transferAmount = Number(transaction?.transferAmount);
    if (
      !/^\d{6,24}$/.test(accountNumber) ||
      !/^\d{1,32}$/.test(transactionId) ||
      !Number.isSafeInteger(transferAmount) ||
      transferAmount <= 0
    ) {
      return json({ success: false, message: "Invalid transaction fields" }, 400);
    }

    const reference = extractPaymentReference(
      `${String(transaction?.content ?? "")} ${String(transaction?.code ?? "")}`,
    );
    if (!reference) {
      return json({ success: true, message: "No Oishi payment reference" });
    }

    try {
      const serviceAccount = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT_JSON);
      const databaseUrl = getDatabaseUrl(serviceAccount, env.FIREBASE_DATABASE_URL);
      const accessToken = await getFirebaseAccessToken(serviceAccount);
      const store = await firebaseGet(
        databaseUrl,
        `stores/${storeId}`,
        accessToken,
      );
      if (!store || normalizeAccountNumber(store.bankAccountNumber) !== accountNumber) {
        return json({ success: true, message: "Receiving account did not match store" });
      }

      const transactionKey = `${accountNumber}_${transactionId}`;
      const existingTransaction = await firebaseGet(
        databaseUrl,
        `sepay_transactions/${transactionKey}`,
        accessToken,
      );
      if (existingTransaction) {
        return json({ success: true, message: "Transaction already processed" });
      }

      const orders = await firebaseQueryOrderByReference(
        databaseUrl,
        reference,
        accessToken,
      );
      const matches = Object.entries(orders ?? {}).filter(
        ([, order]) =>
          order?.paymentReference === reference &&
          (order?.storeId || order?.shopId) === storeId,
      );
      if (matches.length !== 1) {
        return json({
          success: true,
          message: matches.length === 0 ? "No matching order" : "Ambiguous order reference",
        });
      }

      const [orderId] = matches[0];
      const orderSnapshot = await firebaseGetWithEtag(
        databaseUrl,
        `orders/${orderId}`,
        accessToken,
      );
      const order = orderSnapshot.value;
      if (
        order?.paymentReference !== reference ||
        (order?.storeId || order?.shopId) !== storeId
      ) {
        return json({ success: true, message: "Order no longer matches" });
      }

      if (order.paymentStatus === "PAID") {
        if (order.sepayTransactionId === transactionId) {
          await recordTransaction(databaseUrl, accessToken, transactionKey, {
            transactionId,
            accountNumber,
            paymentReference: reference,
            orderId,
            amount: transferAmount,
            processedAt: order.paidAt || Date.now(),
          });
        }
        return json({ success: true, message: "Order already processed" });
      }

      if (
        order.paymentMethod !== "BANKING" ||
        order.paymentStatus === "PAID" ||
        order.status !== "AWAITING_PAYMENT"
      ) {
        return json({ success: true, message: "Order is not awaiting bank payment" });
      }

      const expectedAmount =
        order.paymentMode === "DEPOSIT"
          ? Number(order.depositAmount)
          : Number(order.totalPayment || order.total);
      if (!Number.isSafeInteger(expectedAmount) || expectedAmount <= 0 || transferAmount !== expectedAmount) {
        return json({ success: true, message: "Transfer amount did not match order" });
      }

      const paidAt = Date.now();
      const paidFlags =
        order.paymentMode === "DEPOSIT"
          ? { isDepositPaid: true }
          : { isPaid: true };
      const orderUpdated = await firebasePutIfMatch(
        databaseUrl,
        `orders/${orderId}`,
        accessToken,
        orderSnapshot.etag,
        {
          ...order,
          paymentStatus: "PAID",
          paidAt,
          sepayTransactionId: transactionId,
          status: "PENDING",
          orderStatus: "PENDING",
          ...paidFlags,
        },
      );
      if (!orderUpdated) {
        const latestOrder = await firebaseGet(
          databaseUrl,
          `orders/${orderId}`,
          accessToken,
        );
        if (latestOrder?.sepayTransactionId !== transactionId) {
          return json({ success: true, message: "Order was processed concurrently" });
        }
      }
      await recordTransaction(databaseUrl, accessToken, transactionKey, {
        transactionId,
        accountNumber,
        paymentReference: reference,
        orderId,
        amount: transferAmount,
        processedAt: paidAt,
      });

      return json({ success: true });
    } catch (error) {
      console.error(
        "SePay webhook processing failed",
        error instanceof Error ? error.message : "unknown error",
      );
      return json({ success: false, message: "Failed to verify or update payment" }, 500);
    }
  },
};

/**
 * Xác thực chữ ký số HMAC-SHA256 (x-sepay-signature) và thời gian hợp lệ từ SePay Webhook.
 */
async function verifySePaySignature(headers, body, secret) {
  const timestamp = headers.get("x-sepay-timestamp") ?? "";
  const supplied = headers.get("x-sepay-signature") ?? "";
  if (!/^\d{1,12}$/.test(timestamp) || !/^sha256=[a-f0-9]{64}$/i.test(supplied)) {
    return false;
  }

  const timestampSeconds = Number(timestamp);
  if (
    !Number.isSafeInteger(timestampSeconds) ||
    Math.abs(Date.now() / 1000 - timestampSeconds) > MAX_SIGNATURE_AGE_SECONDS
  ) {
    return false;
  }

  const key = await crypto.subtle.importKey(
    "raw",
    encoder.encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const digest = new Uint8Array(
    await crypto.subtle.sign(
      "HMAC",
      key,
      encoder.encode(`${timestamp}.${body}`),
    ),
  );
  const expected = `sha256=${Array.from(digest, (byte) =>
    byte.toString(16).padStart(2, "0"),
  ).join("")}`;
  return constantTimeEqual(expected.toLowerCase(), supplied.toLowerCase());
}

function constantTimeEqual(left, right) {
  if (left.length !== right.length) return false;
  let difference = 0;
  for (let index = 0; index < left.length; index += 1) {
    difference |= left.charCodeAt(index) ^ right.charCodeAt(index);
  }
  return difference === 0;
}

/**
 * Chuẩn hóa số tài khoản ngân hàng, chỉ giữ lại các ký tự số.
 */
function normalizeAccountNumber(value) {
  return String(value ?? "").replace(/\D/g, "");
}

/**
 * Trích xuất mã nhận diện đơn hàng chuẩn dạng OI + 12 ký tự hex từ nội dung/code giao dịch SePay.
 */
function extractPaymentReference(value) {
  const matches = [...String(value).toUpperCase().matchAll(PAYMENT_REFERENCE_PATTERN)];
  return matches.length === 1 ? matches[0][0] : "";
}

function getDatabaseUrl(serviceAccount, configuredUrl) {
  const projectId = serviceAccount?.project_id;
  if (
    typeof projectId !== "string" ||
    !/^[a-z0-9-]+$/.test(projectId) ||
    typeof configuredUrl !== "string"
  ) {
    throw new Error("Invalid Firebase database configuration");
  }
  const parsedUrl = new URL(configuredUrl);
  if (
    parsedUrl.protocol !== "https:" ||
    parsedUrl.pathname !== "/" ||
    parsedUrl.search !== "" ||
    parsedUrl.hash !== "" ||
    !(
      parsedUrl.hostname === `${projectId}-default-rtdb.asia-southeast1.firebasedatabase.app` ||
      parsedUrl.hostname === `${projectId}-default-rtdb.firebaseio.com`
    )
  ) {
    throw new Error("Firebase database URL does not match project");
  }
  return parsedUrl.origin;
}

async function getFirebaseAccessToken(serviceAccount) {
  if (cachedFirebaseToken && cachedFirebaseToken.expiresAt > Date.now() + 60_000) {
    return cachedFirebaseToken.value;
  }
  if (
    typeof serviceAccount?.client_email !== "string" ||
    typeof serviceAccount?.private_key !== "string"
  ) {
    throw new Error("Invalid Firebase service account");
  }

  const nowSeconds = Math.floor(Date.now() / 1000);
  const header = base64UrlEncode(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = base64UrlEncode(
    JSON.stringify({
      iss: serviceAccount.client_email,
      scope: FIREBASE_SCOPE,
      aud: FIREBASE_TOKEN_URL,
      iat: nowSeconds,
      exp: nowSeconds + 3600,
    }),
  );
  const signingInput = `${header}.${claims}`;
  const privateKey = await crypto.subtle.importKey(
    "pkcs8",
    pemToArrayBuffer(serviceAccount.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    privateKey,
    encoder.encode(signingInput),
  );
  const assertion = `${signingInput}.${base64UrlEncodeBytes(new Uint8Array(signature))}`;
  const response = await fetch(FIREBASE_TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
    signal: AbortSignal.timeout(10_000),
  });
  const result = await response.json();
  if (!response.ok || typeof result.access_token !== "string") {
    throw new Error("Firebase token exchange failed");
  }

  cachedFirebaseToken = {
    value: result.access_token,
    expiresAt: Date.now() + Number(result.expires_in || 3600) * 1000,
  };
  return cachedFirebaseToken.value;
}

function pemToArrayBuffer(pem) {
  const base64 = pem
    .replace(/-----BEGIN PRIVATE KEY-----/g, "")
    .replace(/-----END PRIVATE KEY-----/g, "")
    .replace(/\s/g, "");
  const binary = atob(base64);
  return Uint8Array.from(binary, (character) => character.charCodeAt(0)).buffer;
}

function base64UrlEncode(value) {
  return base64UrlEncodeBytes(encoder.encode(value));
}

function base64UrlEncodeBytes(bytes) {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}

async function firebaseGet(databaseUrl, path, accessToken) {
  const response = await fetch(
    `${databaseUrl}/${path}.json`,
    {
      headers: { Authorization: `Bearer ${accessToken}` },
      signal: AbortSignal.timeout(10_000),
    },
  );
  if (!response.ok) throw await firebaseResponseError("Firebase read", response);
  return response.json();
}

async function firebaseGetWithEtag(databaseUrl, path, accessToken) {
  const response = await fetch(`${databaseUrl}/${path}.json`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "X-Firebase-ETag": "true",
    },
    signal: AbortSignal.timeout(10_000),
  });
  if (!response.ok) {
    throw await firebaseResponseError("Firebase order read", response);
  }
  return { value: await response.json(), etag: response.headers.get("ETag") };
}

async function firebaseQueryOrderByReference(databaseUrl, reference, accessToken) {
  const query = new URLSearchParams({
    orderBy: JSON.stringify("paymentReference"),
    equalTo: JSON.stringify(reference),
  });
  const response = await fetch(`${databaseUrl}/orders.json?${query}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
    signal: AbortSignal.timeout(10_000),
  });
  if (!response.ok) {
    throw await firebaseResponseError("Firebase order lookup", response);
  }
  return response.json();
}

async function firebasePutIfMatch(databaseUrl, path, accessToken, etag, value) {
  if (!etag) throw new Error("Firebase order ETag is missing");
  const response = await fetch(`${databaseUrl}/${path}.json`, {
    method: "PUT",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
      "if-match": etag,
    },
    body: JSON.stringify(value),
    signal: AbortSignal.timeout(10_000),
  });
  if (response.status === 412) return false;
  if (!response.ok) {
    throw await firebaseResponseError("Firebase payment update", response);
  }
  return true;
}

async function recordTransaction(databaseUrl, accessToken, transactionKey, transaction) {
  await firebasePatch(databaseUrl, accessToken, {
    [`sepay_transactions/${transactionKey}`]: transaction,
  });
}

async function firebasePatch(databaseUrl, accessToken, updates) {
  const response = await fetch(`${databaseUrl}/.json`, {
    method: "PATCH",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(updates),
    signal: AbortSignal.timeout(10_000),
  });
  if (!response.ok) {
    throw await firebaseResponseError("Firebase payment update", response);
  }
}

async function firebaseResponseError(operation, response) {
  const body = (await response.text())
    .replace(/[\u0000-\u001f\u007f]/g, " ")
    .slice(0, 300);
  return new Error(`${operation} failed (${response.status}): ${body || "empty response"}`);
}

function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });
}
