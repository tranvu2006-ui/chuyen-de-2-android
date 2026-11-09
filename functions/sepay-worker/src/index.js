// Dùng chung bộ mã hóa UTF-8 khi tạo chữ ký và JWT gửi Firebase.
const encoder = new TextEncoder();
// Giới hạn kích thước webhook để tránh nhận payload bất thường/quá lớn.
const MAX_BODY_BYTES = 32_768;
// Chấp nhận timestamp chữ ký trong khoảng 5 phút để hạn chế webhook phát lại.
const MAX_SIGNATURE_AGE_SECONDS = 300;
// Endpoint OAuth của Google dùng đổi JWT service account thành access token Firebase.
const FIREBASE_TOKEN_URL = "https://oauth2.googleapis.com/token";
// Worker chỉ cần hai quyền: đọc danh tính service account và truy cập Realtime Database.
const FIREBASE_SCOPE =
  "https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/firebase.database";
// Mẫu mã tham chiếu thanh toán được hỗ trợ; mỗi payload chỉ được khớp một mã duy nhất.
const PAYMENT_REFERENCE_PATTERN = /\b(?:OI[A-Z0-9]{8}|OI(?:[A-F0-9]{12}|[A-F0-9]{16})|HD[A-F0-9]{12})\b/g;
// Mẫu mã đơn chuẩn dùng cho webhook tiền vào/ra.
const ORDER_ID_PATTERN = /\b(?:OI[A-Z0-9]{8}|OI[A-F0-9]{16})\b/g;

// Cache token trong isolate để giảm số lần gọi Google OAuth; token sắp hết hạn sẽ được làm mới.
let cachedFirebaseToken = null;

/**
 * Endpoint tiếp nhận SePay Webhook xử lý thanh toán vào và hoàn tiền ra.
 * - Xác thực HMAC-SHA256 theo từng storeId của quán.
 * - Ghép mã đơn/mã hoàn tiền; đối chiếu tài khoản cửa hàng, số tiền và trạng thái.
 * - Cập nhật trạng thái thanh toán/hoàn tiền trên Firebase Realtime Database.
 */
export default {
  async fetch(request, env) {
    // Chỉ nhận POST tại đúng endpoint webhook, các đường dẫn/phương thức khác không được xử lý.
    const url = new URL(request.url);
    if (request.method !== "POST" || url.pathname !== "/webhook") {
      return json({ success: false, message: "Not found" }, 404);
    }

    // Secret dùng làm phương án dự phòng; trên môi trường thật nên đặt bằng Cloudflare Secret,
    // không để secret dùng chung trong mã nguồn hoặc cấu hình được commit.
    const masterSecret = String(env.SEPAY_MASTER_SECRET || "oishi_master_secret_2026").trim();
    // Worker cần secret để xác thực webhook và service account để ghi trạng thái lên Firebase.
    if ((!env.SEPAY_WEBHOOK_SECRETS_JSON && !masterSecret) || !env.FIREBASE_SERVICE_ACCOUNT_JSON) {
      return json({ success: false, message: "Webhook is not configured" }, 503);
    }

    // storeId xác định cửa hàng để tra secret riêng, tài khoản ngân hàng đã liên kết và đơn hàng.
    const storeId = url.searchParams.get("storeId") ?? "";
    if (!/^[A-Za-z0-9_-]{1,128}$/.test(storeId)) {
      return json({ success: false, message: "Invalid store" }, 400);
    }

    // Đọc nguyên văn body vì chữ ký HMAC được tính trên timestamp cộng chính xác chuỗi body này.
    const body = await request.text();
    if (encoder.encode(body).byteLength > MAX_BODY_BYTES) {
      return json({ success: false, message: "Request too large" }, 413);
    }

    // Parse JSON sau khi giới hạn kích thước; body sai định dạng bị từ chối.
    let transaction;
    try {
      transaction = JSON.parse(body);
    } catch {
      return json({ success: false, message: "Invalid JSON" }, 400);
    }

    // Phân biệt tiền vào (thanh toán) và tiền ra (hoàn tiền); loại khác không cập nhật đơn.
    const transferType = String(transaction?.transferType ?? "").toLowerCase();
    if (transferType !== "in" && transferType !== "out") {
      return json({ success: true, message: "Ignored unsupported transfer type" });
    }

    if (!env.SEPAY_WEBHOOK_SECRETS_JSON && !masterSecret) {
      return json({ success: false, message: "Webhook is not configured" }, 503);
    }

    // Đọc bản đồ {storeId: secret} của webhook tiền vào; JSON lỗi là lỗi cấu hình, không bỏ qua.
    let paymentWebhookSecrets;
    try {
      paymentWebhookSecrets = JSON.parse(env.SEPAY_WEBHOOK_SECRETS_JSON);
    } catch {
      return json({ success: false, message: "Webhook is not configured" }, 503);
    }
    // Có thể đặt secret riêng cho webhook hoàn tiền; nếu không có sẽ dùng secret tiền vào của cửa hàng.
    let refundWebhookSecrets = {};
    if (transferType === "out" && env.SEPAY_REFUND_WEBHOOK_SECRETS_JSON) {
      try {
        refundWebhookSecrets = JSON.parse(env.SEPAY_REFUND_WEBHOOK_SECRETS_JSON);
      } catch {
        return json({ success: false, message: "Webhook is not configured" }, 503);
      }
    }
    // Ưu tiên secret theo cửa hàng/loại giao dịch, sau đó mới dùng secret dự phòng.
    const specificSecret = transferType === "out"
      ? refundWebhookSecrets?.[storeId] || paymentWebhookSecrets?.[storeId]
      : paymentWebhookSecrets?.[storeId];
    const webhookSecret = (typeof specificSecret === "string" && specificSecret.trim())
      ? specificSecret.trim()
      : masterSecret;
    if (typeof webhookSecret !== "string" || !webhookSecret) {
      return json({ success: false, message: "Invalid webhook signature" }, 401);
    }

    // Hỗ trợ HMAC có timestamp; đồng thời giữ tương thích header API key theo cấu hình SePay hiện có.
    const isHmacValid = await verifySePaySignature(request.headers, body, webhookSecret);
    const isApiKeyValid = verifySePayApiKey(request.headers, webhookSecret);
    if (!isHmacValid && !isApiKeyValid) {
      return json({ success: false, message: "Invalid webhook signature" }, 401);
    }

    // Chuẩn hóa và xác thực các trường giao dịch trước khi truy vấn Firebase.
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

    // SePay có thể đưa mã tham chiếu vào content hoặc code; tìm mã đơn từ cả hai trường.
    const transferReferenceText =
      `${String(transaction?.content ?? "")} ${String(transaction?.code ?? "")}`;
    const orderIdFromContent = extractOrderId(transferReferenceText);
    const legacyReference = orderIdFromContent
      ? ""
      : extractPaymentReference(transferReferenceText);

    try {
      // Dùng service account phía Worker (không phải app Android) để truy cập Firebase có quyền ghi.
      const serviceAccount = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT_JSON);
      const databaseUrl = getDatabaseUrl(serviceAccount, env.FIREBASE_DATABASE_URL);
      const accessToken = await getFirebaseAccessToken(serviceAccount);
      const store = await firebaseGet(
        databaseUrl,
        `stores/${storeId}`,
        accessToken,
      );
      if (!store || normalizeAccountNumber(store.bankAccountNumber) !== accountNumber) {
        return json({ success: true, message: "Connected account did not match store" });
      }

      // Tiền ra được xử lý riêng và chỉ xác nhận hoàn nếu tồn tại đơn đã hủy, đang chờ hoàn, đúng mã/số tiền.
      if (transferType === "out") {
        return await processOutgoingRefund({
          databaseUrl,
          accessToken,
          storeId,
          transactionId,
          transferAmount,
          content: `${String(transaction?.content ?? "")} ${String(transaction?.code ?? "")}`,
        });
      }

      // ── BƯỚC 1: Tìm đúng một đơn đang thanh toán theo mã trong nội dung chuyển khoản ───────────
      let matches;
      if (orderIdFromContent) {
        // Trường hợp tìm trực tiếp bằng orderId trích xuất từ nội dung CK (ví dụ: ORD_xxx)
        const order = await firebaseGet(
          databaseUrl,
          `orders/${orderIdFromContent}`,
          accessToken,
        );
        matches = order ? [[orderIdFromContent, order]] : [];
        // Chỉ đối soát thanh toán ngân hàng, không tự xác nhận đơn MoMo
        matches = matches.filter(
          ([, candidate]) =>
            ["BANKING", "MOMO"].includes(candidate?.paymentMethod) &&
            (candidate?.storeId || candidate?.shopId) === storeId,
        );
      } else if (legacyReference) {
        // Trường hợp fallback: tra cứu bằng paymentReference (cú pháp cũ)
        const orders = await firebaseQueryOrderByReference(
          databaseUrl,
          legacyReference,
          accessToken,
        );
        matches = Object.entries(orders ?? {}).filter(
          ([, order]) =>
            order?.paymentReference === legacyReference &&
            ["BANKING", "MOMO"].includes(order?.paymentMethod) &&
            (order?.storeId || order?.shopId) === storeId,
        );
      } else {
        matches = []; // Không tìm thấy mã nào trong nội dung CK → bỏ qua
      }
      // Phải khớp đúng 1 đơn: 0 = không tìm thấy, >1 = mơ hồ → từ chối xử lý
      if (matches.length !== 1) {
        return json({
          success: true,
          message: matches.length === 0
            ? "No matching order"
            : "Ambiguous order reference",
        });
      }

      // ── BƯỚC 2: Đọc lại đơn cùng ETag để phát hiện dữ liệu bị thay đổi đồng thời ──────────────
      const [orderId] = matches[0];
      const matchedPaymentReference = String(matches[0][1].paymentReference);
      // Dùng ETag để đảm bảo không có worker khác ghi đè đồng thời
      const orderSnapshot = await firebaseGetWithEtag(
        databaseUrl,
        `orders/${orderId}`,
        accessToken,
      );
      const order = orderSnapshot.value;
      // Kiểm tra lại: đơn vẫn còn khớp với storeId và paymentReference
      if (
        order?.paymentReference !== matchedPaymentReference ||
        (order?.storeId || order?.shopId) !== storeId
      ) {
        return json({ success: true, message: "Order no longer matches" });
      }

      // ── BƯỚC 3: Chống xử lý trùng lặp (idempotency) ───────────────────────────────
      if (order.paymentStatus === "PAID") {
        // The order's sepayTransactionId prevents a payment webhook from being applied twice.
        return json({ success: true, message: "Order already processed" });
      }

      // ── BƯỚC 4: Kiểm tra điều kiện đơn hàng hợp lệ ────────────────────────────────
      // Đơn phải là BANKING hoặc MOMO, chưa PAID, đang chờ thanh toán
      if (
        (order.paymentMethod !== "BANKING" && order.paymentMethod !== "MOMO") ||
        order.paymentStatus === "PAID" ||
        order.status !== "AWAITING_PAYMENT"
      ) {
        return json({ success: true, message: "Order is not awaiting bank payment" });
      }

      // ── BƯỚC 5: Kiểm tra số tiền chuyển khớp đúng với tổng đơn ──────────────────
      const expectedAmount = getExpectedPaymentAmount(order);
      // Nếu số tiền nhận được khác với số tiền đơn → từ chối (tránh thiếu/thừa tiền)
      if (!Number.isSafeInteger(expectedAmount) || expectedAmount <= 0 || transferAmount !== expectedAmount) {
        return json({ success: true, message: "Transfer amount did not match order" });
      }

      // Đến đây webhook đã khớp mã giao dịch/đơn, quán, phương thức, trạng thái chờ thanh toán và số tiền cần trả;
      // bước kiểm tra phía trên cũng bỏ qua giao dịch đã xử lý để tránh ghi nhận thanh toán hai lần.
      // Worker chỉ tự động GHI NHẬN khoản tiền đã chuyển bằng cách cập nhật Firebase; nó không trừ tiền hay chuyển tiền.
      // ── BƯỚC 6: Cập nhật Firebase — đánh dấu PAID và chuyển sang PENDING ─────────
      const paidAt = Date.now(); // Thời điểm ghi nhận thanh toán (Unix ms)
      // Đơn cọc chỉ bật isDepositPaid; đơn trả toàn bộ bật isPaid để các màn hình tính phần còn phải thu chính xác.
      const paidFlags =
        order.paymentMode === "DEPOSIT"
          ? { isDepositPaid: true }  // Đã cọc 20%
          : { isPaid: true };         // Đã thanh toán 100%
      // Ghi nguyên tử lên Firebase với ETag — chỉ ghi nếu dữ liệu chưa bị thay đổi
      const orderUpdated = await firebasePutIfMatch(
        databaseUrl,
        `orders/${orderId}`,
        accessToken,
        orderSnapshot.etag,
        {
          ...order,
          paymentStatus: "PAID",           // Đánh dấu đã thanh toán
          paidAt,                           // Lưu thời điểm thanh toán
          sepayTransactionId: transactionId, // ID giao dịch SePay để chống trùng
          status: "PENDING",                // Chuyển đơn sang hàng đợi xử lý
          orderStatus: "PENDING",           // Đồng bộ field orderStatus (legacy)
          ...paidFlags,
        },
      );
      // Nếu ETag không khớp (worker khác đã ghi trước) → kiểm tra có phải trùng không
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
      return json({ success: true }); // Trả về 200 OK để SePay không gửi lại webhook
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
  // Chữ ký phải gồm timestamp số và digest SHA-256 đúng định dạng.
  const timestamp = headers.get("x-sepay-timestamp") ?? "";
  const supplied = headers.get("x-sepay-signature") ?? "";
  if (!/^\d{1,12}$/.test(timestamp) || !/^sha256=[a-f0-9]{64}$/i.test(supplied)) {
    return false;
  }

  // Từ chối chữ ký quá cũ/quá xa thời gian hiện tại để giảm nguy cơ phát lại webhook.
  const timestampSeconds = Number(timestamp);
  if (
    !Number.isSafeInteger(timestampSeconds) ||
    Math.abs(Date.now() / 1000 - timestampSeconds) > MAX_SIGNATURE_AGE_SECONDS
  ) {
    return false;
  }

  // Tạo khóa HMAC từ secret cấu hình; ký timestamp.body theo giao thức webhook.
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
  // Chuyển digest thành chuỗi hex theo định dạng header SePay rồi so sánh an toàn.
  const expected = `sha256=${Array.from(digest, (byte) =>
    byte.toString(16).padStart(2, "0"),
  ).join("")}`;
  return constantTimeEqual(expected.toLowerCase(), supplied.toLowerCase());
}

function constantTimeEqual(left, right) {
  // So sánh toàn bộ chuỗi thay vì dừng ở ký tự sai đầu tiên để giảm rò rỉ thời gian.
  if (left.length !== right.length) return false;
  let difference = 0;
  for (let index = 0; index < left.length; index += 1) {
    difference |= left.charCodeAt(index) ^ right.charCodeAt(index);
  }
  return difference === 0;
}

function verifySePayApiKey(headers, secret) {
  // Tương thích xác thực kiểu Authorization: Apikey/Bearer nếu SePay được cấu hình theo API key.
  const authHeader = headers.get("authorization") ?? "";
  if (!authHeader) return false;
  const match = authHeader.match(/^(?:Apikey|Bearer)\s+(.+)$/i);
  if (!match) return false;
  return constantTimeEqual(match[1].trim(), secret);
}

function normalizeAccountNumber(value) {
  // Loại dấu cách/ký tự phân cách để so sánh số tài khoản nhất quán.
  return String(value ?? "").replace(/\D/g, "");
}

/**
 * Trích xuất mã đơn hàng VietQR từ nội dung/code giao dịch SePay.
 */
function extractPaymentReference(value) {
  // Chỉ trả mã khi tìm thấy đúng một tham chiếu; nhiều mã trong nội dung là mơ hồ nên không khớp.
  const matches = [...String(value).toUpperCase().matchAll(PAYMENT_REFERENCE_PATTERN)];
  return matches.length === 1 ? matches[0][0] : "";
}

function extractOrderId(value) {
  // Quy tắc tương tự mã thanh toán nhưng giới hạn vào định dạng order ID chuẩn.
  const matches = [...String(value).toUpperCase().matchAll(ORDER_ID_PATTERN)];
  return matches.length === 1 ? matches[0][0] : "";
}

function extractRefundReference(value) {
  return extractOrderId(value);
}

async function processOutgoingRefund({
  databaseUrl,
  accessToken,
  storeId,
  transactionId,
  transferAmount,
  content,
}) {
  // Mã đơn trong nội dung chuyển khoản là khóa liên kết giao dịch tiền ra với yêu cầu hoàn.
  const refundReference = extractRefundReference(content);
  if (!refundReference) {
    return json({ success: true, message: "No refund reference" });
  }

  // Tra các đơn theo mã hoàn, sau đó bắt buộc khớp cửa hàng, đơn đã hủy, trạng thái và số tiền chính xác.
  const matchingOrders = await firebaseQueryOrderByRefundReference(
    databaseUrl,
    refundReference,
    accessToken,
  );
  const matches = Object.entries(matchingOrders ?? {}).filter(
    ([, order]) =>
      order?.refundReference === refundReference &&
      (order?.storeId || order?.shopId) === storeId &&
      order?.status === "CANCELLED" &&
      order?.refundStatus === "PENDING_REFUND" &&
      Number(order?.refundAmount) === transferAmount,
  );
  if (matches.length !== 1) {
    return json({
      success: true,
      message: matches.length === 0
        ? "No matching pending refund"
        : "Ambiguous refund reference",
    });
  }

  // Đọc mới nhất kèm ETag trước khi ghi để tránh ghi đè thay đổi đồng thời.
  const [orderId] = matches[0];
  const orderSnapshot = await firebaseGetWithEtag(
    databaseUrl,
    `orders/${orderId}`,
    accessToken,
  );
  const order = orderSnapshot.value;
  if (
    order?.refundReference !== refundReference ||
    (order?.storeId || order?.shopId) !== storeId ||
    order?.status !== "CANCELLED" ||
    order?.refundStatus !== "PENDING_REFUND" ||
    Number(order?.refundAmount) !== transferAmount
  ) {
    return json({ success: true, message: "Refund no longer matches" });
  }

  // Chỉ ghi nhận webhook sau khi đối chiếu thành công; Worker không thực hiện lệnh chuyển tiền.
  const refundedAt = Date.now();
  const refundUpdated = await firebasePutIfMatch(
    databaseUrl,
    `orders/${orderId}`,
    accessToken,
    orderSnapshot.etag,
    {
      ...order,
      refundStatus: "REFUNDED",
      refundTransactionId: transactionId,
      refundedBy: "SEPAY_WEBHOOK",
      refundedAt,
      updatedAt: refundedAt,
    },
  );
  if (!refundUpdated) {
    const latestOrder = await firebaseGet(
      databaseUrl,
      `orders/${orderId}`,
      accessToken,
    );
    if (latestOrder?.refundTransactionId !== transactionId) {
      return json({ success: true, message: "Refund was processed concurrently" });
    }
  }

  return json({ success: true, message: "Refund confirmed" });
}

function getDatabaseUrl(serviceAccount, configuredUrl) {
  // Đảm bảo database URL thuộc đúng project service account và chỉ dùng HTTPS host được hỗ trợ.
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
  // Dùng token còn hạn trong cache; làm mới trước khi hết hạn để tránh lỗi giữa yêu cầu.
  if (cachedFirebaseToken && cachedFirebaseToken.expiresAt > Date.now() + 60_000) {
    return cachedFirebaseToken.value;
  }
  if (
    typeof serviceAccount?.client_email !== "string" ||
    typeof serviceAccount?.private_key !== "string"
  ) {
    throw new Error("Invalid Firebase service account");
  }

  // Tạo JWT service-account assertion có thời hạn một giờ để xin OAuth access token.
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
  // Gửi assertion ký RSA tới Google OAuth; private key chỉ được nạp từ secret phía Worker.
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
  // Bỏ khung PEM và giải mã khóa riêng PKCS#8 thành bytes cho Web Crypto.
  const base64 = pem
    .replace(/-----BEGIN PRIVATE KEY-----/g, "")
    .replace(/-----END PRIVATE KEY-----/g, "")
    .replace(/\s/g, "");
  const binary = atob(base64);
  return Uint8Array.from(binary, (character) => character.charCodeAt(0)).buffer;
}

function base64UrlEncode(value) {
  // JWT dùng base64url, không phải base64 có padding chuẩn.
  return base64UrlEncodeBytes(encoder.encode(value));
}

function base64UrlEncodeBytes(bytes) {
  // Mã hóa bytes sang base64url và bỏ padding theo định dạng JWT.
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}

async function firebaseGet(databaseUrl, path, accessToken) {
  // Đọc node Realtime Database bằng access token của service account.
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
  // Yêu cầu Firebase trả ETag để ghi có điều kiện, tránh cập nhật dựa trên snapshot cũ.
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
  // Tìm các đơn theo paymentReference để hỗ trợ định dạng mã thanh toán cũ.
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

async function firebaseQueryOrderByRefundReference(databaseUrl, reference, accessToken) {
  // Tìm đơn đang gắn với mã hoàn tiền (hiện mã hoàn là mã đơn).
  const query = new URLSearchParams({
    orderBy: JSON.stringify("refundReference"),
    equalTo: JSON.stringify(reference),
  });
  const response = await fetch(`${databaseUrl}/orders.json?${query}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
    signal: AbortSignal.timeout(10_000),
  });
  if (!response.ok) {
    throw await firebaseResponseError("Firebase refund lookup", response);
  }
  return response.json();
}

async function firebaseQueryOrdersByPaymentMethod(databaseUrl, paymentMethod, accessToken) {
  // Truy vấn đơn theo phương thức thanh toán cho các luồng đối soát liên quan.
  const query = new URLSearchParams({
    orderBy: JSON.stringify("paymentMethod"),
    equalTo: JSON.stringify(paymentMethod),
  });
  const response = await fetch(`${databaseUrl}/orders.json?${query}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
    signal: AbortSignal.timeout(10_000),
  });
  if (!response.ok) {
    throw await firebaseResponseError("Firebase MoMo order lookup", response);
  }
  return response.json();
}

function getExpectedPaymentAmount(order) {
  // Đơn cọc đối soát đúng tiền cọc; các đơn khác dùng tổng tiền phải thanh toán.
  return order?.paymentMode === "DEPOSIT"
    ? Number(order.depositAmount)
    : Number(order?.totalPayment || order?.total);
}

function isRecentMomoOrder(order, now) {
  // Giới hạn độ tuổi đơn MoMo để tránh gắn giao dịch cũ vào đơn không còn phù hợp.
  const createdAt = Number(order?.createdAt || order?.timestamp);
  const age = now - createdAt;
  return Number.isSafeInteger(createdAt) &&
    age >= -ALLOWED_FUTURE_ORDER_SKEW_MS &&
    age <= MAX_MOMO_ORDER_AGE_MS;
}

async function firebasePutIfMatch(databaseUrl, path, accessToken, etag, value) {
  // PUT kèm ETag; HTTP 412 nghĩa là dữ liệu đã đổi và caller cần đọc lại để xử lý trùng.
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

async function firebaseResponseError(operation, response) {
  // Giới hạn nội dung lỗi Firebase được ghi log để tránh log response lớn/điều khiển dòng.
  const body = (await response.text())
    .replace(/[\u0000-\u001f\u007f]/g, " ")
    .slice(0, 300);
  return new Error(`${operation} failed (${response.status}): ${body || "empty response"}`);
}

function json(body, status = 200) {
  // Chuẩn hóa JSON response để SePay nhận được HTTP status và thông điệp xử lý.
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });
}
