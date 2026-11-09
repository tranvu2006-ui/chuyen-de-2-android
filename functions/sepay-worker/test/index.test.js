import { createHmac, generateKeyPairSync } from "node:crypto";
import { test } from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";

const secret = "local-test-secret";
const env = {
  SEPAY_WEBHOOK_SECRETS_JSON: JSON.stringify({ store1: secret }),
  SEPAY_REFUND_WEBHOOK_SECRETS_JSON: JSON.stringify({ store1: secret }),
  FIREBASE_SERVICE_ACCOUNT_JSON: "{}",
  FIREBASE_DATABASE_URL:
    "https://chuyen-de-2-2f0bc-default-rtdb.asia-southeast1.firebasedatabase.app",
};

function signedRequest(
  payload,
  timestamp = Math.floor(Date.now() / 1000),
  requestSecret = secret,
) {
  const body = JSON.stringify(payload);
  const signature = createHmac("sha256", requestSecret)
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

test("ignores unsupported transfer types", async () => {
  const response = await worker.fetch(
    signedRequest({ transferType: "unknown" }),
    env,
  );
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {
    success: true,
    message: "Ignored unsupported transfer type",
  });
});

test("ignores an incoming transfer with no order reference", async () => {
  const { privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
  const serviceAccount = {
    project_id: "chuyen-de-2-2f0bc",
    client_email: "worker-test@example.iam.gserviceaccount.com",
    private_key: privateKey.export({ type: "pkcs8", format: "pem" }),
  };
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async (input) => {
    const url = new URL(input);
    if (url.hostname === "oauth2.googleapis.com") {
      return Response.json({ access_token: "test-token", expires_in: 3600 });
    }
    if (url.pathname === "/stores/store1.json") {
      return Response.json({ bankAccountNumber: "1234567890" });
    }
    if (url.pathname === "/orders.json") {
      return Response.json({});
    }
    throw new Error(`Unexpected Firebase request: ${url.pathname}`);
  };

  try {
    const response = await worker.fetch(
      signedRequest({
        transferType: "in",
        id: 12345,
        accountNumber: "1234567890",
        transferAmount: 2000,
        content: "chuyen tien",
      }),
      {
        ...env,
        FIREBASE_SERVICE_ACCOUNT_JSON: JSON.stringify(serviceAccount),
      },
    );
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), {
      success: true,
      message: "No matching order",
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("confirms a MoMo order paid by transfer", async () => {
  const { privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
  const serviceAccount = {
    project_id: "chuyen-de-2-2f0bc",
    client_email: "worker-test@example.iam.gserviceaccount.com",
    private_key: privateKey.export({ type: "pkcs8", format: "pem" }),
  };
  const requests = [];
  const orderId = "OI637A4F2B";
  const invoiceReference = "HDABCDEF012345";
  const createdAt = Date.now();
  const momoOrder = {
    storeId: "store1",
    paymentReference: invoiceReference,
    paymentMethod: "MOMO",
    paymentMode: "FULL",
    paymentStatus: "UNPAID",
    status: "AWAITING_PAYMENT",
    totalPayment: 2000,
    createdAt,
  };
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
    if (url.pathname === `/orders/${orderId}.json` && (!init.method || init.method === "GET")) {
      return new Response(JSON.stringify(momoOrder), {
        headers: { ETag: '"v1"' },
      });
    }
    if (url.pathname === `/orders/${orderId}.json` && init.method === "PUT") {
      const updatedOrder = JSON.parse(init.body);
      assert.equal(updatedOrder.paymentStatus, "PAID");
      assert.equal(updatedOrder.sepayTransactionId, "998877");
      assert.equal(updatedOrder.status, "PENDING");
      assert.equal(updatedOrder.isPaid, true);
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
        content: orderId,
      }),
      testEnv,
    );

    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), { success: true });
    assert.equal(
      requests.some(
        ({ url, init }) =>
          url.pathname === `/orders/${orderId}.json` && init.method === "PUT",
      ),
      true,
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("confirms a bank transfer by order ID in transfer content or code", async () => {
  const { privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
  const serviceAccount = {
    project_id: "chuyen-de-2-2f0bc",
    client_email: "worker-test@example.iam.gserviceaccount.com",
    private_key: privateKey.export({ type: "pkcs8", format: "pem" }),
  };
  const orderId = "OI0123456789ABCDEF";
  const order = {
    storeId: "store1",
    paymentReference: orderId,
    paymentMethod: "BANKING",
    paymentMode: "FULL",
    paymentStatus: "UNPAID",
    status: "AWAITING_PAYMENT",
    totalPayment: 155000,
    createdAt: Date.now(),
  };
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async (input, init = {}) => {
    const url = new URL(input);
    if (url.hostname === "oauth2.googleapis.com") {
      return Response.json({ access_token: "test-token", expires_in: 3600 });
    }
    if (url.pathname === "/stores/store1.json") {
      return Response.json({ bankAccountNumber: "1234567890" });
    }
    if (url.pathname === `/orders/${orderId}.json` && (!init.method || init.method === "GET")) {
      return new Response(JSON.stringify(order), {
        headers: { ETag: '"v1"' },
      });
    }
    if (url.pathname === `/orders/${orderId}.json` && init.method === "PUT") {
      const updatedOrder = JSON.parse(init.body);
      assert.equal(updatedOrder.paymentStatus, "PAID");
      assert.equal(updatedOrder.sepayTransactionId, "998877");
      return new Response(null, { status: 204 });
    }
    throw new Error(`Unexpected Firebase request: ${url.pathname}`);
  };

  try {
    const response = await worker.fetch(
      signedRequest({
        id: 998877,
        accountNumber: "1234567890",
        transferType: "in",
        transferAmount: 155000,
        gateway: "MBBank",
        description: "NGUYEN VAN A chuyen tien",
        content: "MBVCB.order.CT tu 12345678 NGUYEN VAN A toi 23456789 OISHI tai MB",
        referenceCode: "FT123456",
        senderBankName: "MB",
        code: orderId,
      }),
      {
        ...env,
        FIREBASE_SERVICE_ACCOUNT_JSON: JSON.stringify(serviceAccount),
      },
    );
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), { success: true });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("does not create extra records for an already-paid order", async () => {
  const { privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
  const serviceAccount = {
    project_id: "chuyen-de-2-2f0bc",
    client_email: "worker-test@example.iam.gserviceaccount.com",
    private_key: privateKey.export({ type: "pkcs8", format: "pem" }),
  };
  const orderId = "OI0123456789ABCDEF";
  const order = {
    orderId,
    customerId: "customer1",
    storeId: "store1",
    paymentReference: orderId,
    paymentMethod: "BANKING",
    paymentStatus: "PAID",
    sepayTransactionId: "998877",
    status: "PENDING",
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
    if (url.pathname === `/orders/${orderId}.json`) {
      return Response.json(order);
    }
    throw new Error(`Unexpected Firebase request: ${url.pathname}`);
  };

  try {
    const response = await worker.fetch(
      signedRequest({
        id: 998877,
        accountNumber: "1234567890",
        transferType: "in",
        transferAmount: 155000,
        content: orderId,
      }),
      {
        ...env,
        FIREBASE_SERVICE_ACCOUNT_JSON: JSON.stringify(serviceAccount),
      },
    );
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), {
      success: true,
      message: "Order already processed",
    });
    assert.equal(
      requests.some(({ url }) =>
        url.pathname.startsWith("/sepay_transactions/") ||
        url.pathname.startsWith("/refund_payment_details/") ||
        url.pathname.includes("/sepayTransactions"),
      ),
      false,
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("confirms a refund using the order ID with the shared secret and exact amount", async () => {
  const { privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
  const serviceAccount = {
    project_id: "chuyen-de-2-2f0bc",
    client_email: "worker-test@example.iam.gserviceaccount.com",
    private_key: privateKey.export({ type: "pkcs8", format: "pem" }),
  };
  const orderId = "OI637A4F2B";
  const refundReference = orderId;
  const order = {
    storeId: "store1",
    status: "CANCELLED",
    refundStatus: "PENDING_REFUND",
    refundReference,
    refundAmount: 155000,
  };
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async (input, init = {}) => {
    const url = new URL(input);
    if (url.hostname === "oauth2.googleapis.com") {
      return Response.json({ access_token: "test-token", expires_in: 3600 });
    }
    if (url.pathname === "/stores/store1.json") {
      return Response.json({ bankAccountNumber: "1234567890" });
    }
    if (url.pathname === "/orders.json" && url.searchParams.has("orderBy")) {
      assert.equal(url.searchParams.get("orderBy"), '"refundReference"');
      assert.equal(url.searchParams.get("equalTo"), JSON.stringify(refundReference));
      return Response.json({ [orderId]: order });
    }
    if (url.pathname === `/orders/${orderId}.json` && (!init.method || init.method === "GET")) {
      return new Response(JSON.stringify(order), {
        headers: { ETag: '"v1"' },
      });
    }
    if (url.pathname === `/orders/${orderId}.json` && init.method === "PUT") {
      const updatedOrder = JSON.parse(init.body);
      assert.equal(updatedOrder.refundStatus, "REFUNDED");
      assert.equal(updatedOrder.refundReference, refundReference);
      assert.equal(updatedOrder.refundTransactionId, "998877");
      return new Response(null, { status: 204 });
    }
    throw new Error(`Unexpected Firebase request: ${url.pathname}`);
  };

  try {
    const response = await worker.fetch(
      signedRequest({
        id: 998877,
        accountNumber: "1234567890",
        transferType: "out",
        transferAmount: 155000,
        content: `Hoan tien ${refundReference}`,
        transactionDate: "2026-11-06 13:00:00",
      }),
      {
        ...env,
        SEPAY_REFUND_WEBHOOK_SECRETS_JSON: JSON.stringify({}),
        FIREBASE_SERVICE_ACCOUNT_JSON: JSON.stringify(serviceAccount),
      },
    );
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), {
      success: true,
      message: "Refund confirmed",
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("does not accept a legacy RF code as a refund reference", async () => {
  const { privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
  const serviceAccount = {
    project_id: "chuyen-de-2-2f0bc",
    client_email: "worker-test@example.iam.gserviceaccount.com",
    private_key: privateKey.export({ type: "pkcs8", format: "pem" }),
  };
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async (input) => {
    const url = new URL(input);
    if (url.hostname === "oauth2.googleapis.com") {
      return Response.json({ access_token: "test-token", expires_in: 3600 });
    }
    if (url.pathname === "/stores/store1.json") {
      return Response.json({ bankAccountNumber: "1234567890" });
    }
    throw new Error(`Unexpected Firebase request: ${url.pathname}`);
  };

  try {
    const response = await worker.fetch(
      signedRequest({
        id: 998877,
        accountNumber: "1234567890",
        transferType: "out",
        transferAmount: 155000,
        content: "Hoan tien RF0123456789ABCDEF",
      }),
      {
        ...env,
        FIREBASE_SERVICE_ACCOUNT_JSON: JSON.stringify(serviceAccount),
      },
    );
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), {
      success: true,
      message: "No refund reference",
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});
