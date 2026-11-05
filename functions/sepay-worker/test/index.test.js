import { createHmac, generateKeyPairSync } from "node:crypto";
import { test } from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";

const secret = "local-test-secret";
const env = {
  SEPAY_WEBHOOK_SECRETS_JSON: JSON.stringify({ store1: secret }),
  FIREBASE_SERVICE_ACCOUNT_JSON: "{}",
  FIREBASE_DATABASE_URL:
    "https://chuyen-de-2-2f0bc-default-rtdb.asia-southeast1.firebasedatabase.app",
};

function signedRequest(payload, timestamp = Math.floor(Date.now() / 1000)) {
  const body = JSON.stringify(payload);
  const signature = createHmac("sha256", secret)
    .update(`${timestamp}.${body}`)
    .digest("hex");
  return new Request("https://worker.test/webhook?storeId=store1", {
    method: "POST",
    headers: {
      "x-sepay-timestamp": String(timestamp),
      "x-sepay-signature": `sha256=${signature}`,
    },
    body,
  });
}

test("rejects a webhook with an invalid signature", async () => {
  const request = signedRequest({ transferType: "in" });
  request.headers.set("x-sepay-signature", `sha256=${"0".repeat(64)}`);
  const response = await worker.fetch(request, env);
  assert.equal(response.status, 401);
});

test("rejects a signed webhook with an expired timestamp", async () => {
  const request = signedRequest({ transferType: "in" }, Math.floor(Date.now() / 1000) - 301);
  const response = await worker.fetch(request, env);
  assert.equal(response.status, 401);
});

test("ignores outgoing transfers", async () => {
  const response = await worker.fetch(
    signedRequest({ transferType: "out" }),
    env,
  );
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {
    success: true,
    message: "Ignored non-incoming transaction",
  });
});

test("acknowledges incoming transfers without an Oishi reference", async () => {
  const response = await worker.fetch(
    signedRequest({
      transferType: "in",
      id: 12345,
      accountNumber: "1234567890",
      transferAmount: 2000,
      content: "chuyen tien",
    }),
    env,
  );
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {
    success: true,
    message: "No Oishi payment reference",
  });
});

test("confirms one matching order and records the SePay transaction", async () => {
  const { privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
  const serviceAccount = {
    project_id: "chuyen-de-2-2f0bc",
    client_email: "worker-test@example.iam.gserviceaccount.com",
    private_key: privateKey.export({ type: "pkcs8", format: "pem" }),
  };
  const requests = [];
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async (input, init = {}) => {
    const url = new URL(input);
    requests.push({ url, init });
    if (url.hostname === "oauth2.googleapis.com") {
      return Response.json({ access_token: "test-token", expires_in: 3600 });
    }
    if (url.pathname === "/stores/store1.json") {
      return Response.json({ bankAccountNumber: "1234567890" });
    }
    if (url.pathname === "/sepay_transactions/1234567890_998877.json") {
      return Response.json(null);
    }
    if (url.pathname === "/orders.json") {
      return Response.json({
        order1: {
          storeId: "store1",
          paymentReference: "OI0123456789AB",
        },
      });
    }
    if (url.pathname === "/orders/order1.json" && (!init.method || init.method === "GET")) {
      return new Response(
        JSON.stringify({
          storeId: "store1",
          paymentReference: "OI0123456789AB",
          paymentMethod: "BANKING",
          paymentMode: "FULL",
          paymentStatus: "UNPAID",
          status: "AWAITING_PAYMENT",
          totalPayment: 2000,
        }),
        { headers: { ETag: '"v1"' } },
      );
    }
    if (url.pathname === "/orders/order1.json" && init.method === "PUT") {
      return new Response(null, { status: 204 });
    }
    if (url.pathname === "/.json" && init.method === "PATCH") {
      return new Response(null, { status: 204 });
    }
    throw new Error(`Unexpected Firebase request: ${url.pathname}`);
  };

  try {
    const testEnv = {
      ...env,
      FIREBASE_SERVICE_ACCOUNT_JSON: JSON.stringify(serviceAccount),
    };
    const response = await worker.fetch(
      signedRequest({
        id: 998877,
        accountNumber: "1234567890",
        transferType: "in",
        transferAmount: 2000,
        content: "OISHI OI0123456789AB",
      }),
      testEnv,
    );

    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), { success: true });

    const tokenRequest = requests.find(
      ({ url }) => url.hostname === "oauth2.googleapis.com",
    );
    const assertion = new URLSearchParams(tokenRequest.init.body).get("assertion");
    const claims = JSON.parse(
      Buffer.from(assertion.split(".")[1], "base64url").toString("utf8"),
    );
    assert.equal(
      claims.scope,
      "https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/firebase.database",
    );

    const orderPut = requests.find(
      ({ url, init }) => url.pathname === "/orders/order1.json" && init.method === "PUT",
    );
    assert.equal(orderPut.init.headers["if-match"], '"v1"');
    assert.deepEqual(JSON.parse(orderPut.init.body), {
      storeId: "store1",
      paymentReference: "OI0123456789AB",
      paymentMethod: "BANKING",
      paymentMode: "FULL",
      paymentStatus: "PAID",
      paidAt: JSON.parse(requests.find(({ url }) => url.pathname === "/.json").init.body)[
        "sepay_transactions/1234567890_998877"
      ].processedAt,
      sepayTransactionId: "998877",
      status: "PENDING",
      totalPayment: 2000,
      orderStatus: "PENDING",
      isPaid: true,
    });

    const transactionPatch = JSON.parse(
      requests.find(({ url }) => url.pathname === "/.json").init.body,
    );
    assert.equal(
      transactionPatch["sepay_transactions/1234567890_998877"].paymentReference,
      "OI0123456789AB",
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});
