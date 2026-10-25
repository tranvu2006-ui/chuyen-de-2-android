import { createHmac, randomBytes, timingSafeEqual } from "node:crypto";
import { initializeApp } from "firebase-admin/app";
import { getDatabase } from "firebase-admin/database";
import { setGlobalOptions } from "firebase-functions/v2";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";

initializeApp();
setGlobalOptions({ region: "asia-southeast1", maxInstances: 10 });

const hashSecret = defineSecret("VNPAY_HASH_SECRET");
const merchantCode = "CTY03DS1";
const paymentUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
const returnUrl = "http://localhost:8080";
const paymentLifetimeMs = 15 * 60 * 1000;

type VnPayFields = Record<string, string>;

function encode(value: string): string {
  return encodeURIComponent(value)
    .replace(/[!'()~]/g, (character) =>
      `%${character.charCodeAt(0).toString(16).toUpperCase()}`,
    )
    .replace(/%20/g, "+");
}

function hashData(fields: VnPayFields): string {
  return Object.entries(fields)
    .sort(([left], [right]) => (left < right ? -1 : left > right ? 1 : 0))
    .map(([key, value]) => `${key}=${encode(value)}`)
    .join("&");
}

function formatVnDate(date: Date): string {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: "Asia/Ho_Chi_Minh",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hourCycle: "h23",
  }).formatToParts(date);
  const values = Object.fromEntries(parts.map(({ type, value }) => [type, value]));
  return `${values.year}${values.month}${values.day}${values.hour}${values.minute}${values.second}`;
}

function signedHash(fields: VnPayFields, secret: string): string {
  return createHmac("sha512", secret).update(hashData(fields)).digest("hex");
}

function parseReturnUrl(value: unknown): VnPayFields {
  if (typeof value !== "string" || value.length > 8192) {
    throw new HttpsError("invalid-argument", "VNPay return URL is invalid.");
  }

  let url: URL;
  try {
    url = new URL(value);
  } catch {
    throw new HttpsError("invalid-argument", "VNPay return URL is invalid.");
  }
  if (url.protocol !== "http:" || url.hostname !== "localhost" || url.port !== "8080") {
    throw new HttpsError("invalid-argument", "Unexpected VNPay return URL.");
  }

  const fields: VnPayFields = {};
  for (const key of new Set(url.searchParams.keys())) {
    if (!key.startsWith("vnp_") || url.searchParams.getAll(key).length !== 1) {
      throw new HttpsError("invalid-argument", "VNPay return parameters are invalid.");
    }
    fields[key] = url.searchParams.get(key) ?? "";
  }
  return fields;
}

function verifyHash(fields: VnPayFields, secret: string): boolean {
  const receivedHash = fields.vnp_SecureHash ?? "";
  if (!/^[a-f0-9]{128}$/i.test(receivedHash) || !secret) return false;

  const signedFields = Object.fromEntries(
    Object.entries(fields).filter(
      ([key]) => key !== "vnp_SecureHash" && key !== "vnp_SecureHashType",
    ).filter(([, value]) => value !== ""),
  );
  const expectedHash = Buffer.from(signedHash(signedFields, secret), "hex");
  const actualHash = Buffer.from(receivedHash, "hex");
  return actualHash.length === expectedHash.length && timingSafeEqual(actualHash, expectedHash);
}

function asPositiveSafeInteger(value: unknown): number | null {
  const number = typeof value === "number" ? value : Number(value);
  return Number.isSafeInteger(number) && number > 0 ? number : null;
}

async function clearPendingOrderLock(orderId: string, txnRef: string): Promise<void> {
  await getDatabase().ref(`vnpayPendingOrders/${orderId}`).transaction((current) =>
    current?.txnRef === txnRef ? null : current,
  );
}

export const createVnPayPayment = onCall(
  { secrets: [hashSecret] },
  async (request) => {
    const uid = request.auth?.uid;
    if (!uid) throw new HttpsError("unauthenticated", "Sign in before paying.");

    const orderId = (request.data as { orderId?: unknown } | null)?.orderId;
    if (typeof orderId !== "string" || !/^[A-Za-z0-9_-]{1,128}$/.test(orderId)) {
      throw new HttpsError("invalid-argument", "Order ID is invalid.");
    }

    const secret = hashSecret.value();
    if (!secret) throw new HttpsError("failed-precondition", "VNPay is not configured.");

    const db = getDatabase();
    const orderSnapshot = await db.ref(`orders/${orderId}`).get();
    const order = orderSnapshot.val() as Record<string, unknown> | null;
    if (!order || order.customerId !== uid) {
      throw new HttpsError("not-found", "Order was not found.");
    }
    if (order.paymentMethod !== "VNPAY" || Number(order.totalPayment) !== 0) {
      throw new HttpsError("failed-precondition", "Order is not awaiting VNPay payment.");
    }
    if (order.status !== "PENDING" && order.orderStatus !== "PENDING") {
      throw new HttpsError("failed-precondition", "Order is no longer pending.");
    }
    const amountVnd = asPositiveSafeInteger(order.total);
    if (!amountVnd || amountVnd > Math.floor(Number.MAX_SAFE_INTEGER / 100)) {
      throw new HttpsError("failed-precondition", "Order amount is invalid.");
    }

    const now = new Date();
    const expires = new Date(now.getTime() + paymentLifetimeMs);
    const txnRef = randomBytes(16).toString("hex");
    const lockRef = db.ref(`vnpayPendingOrders/${orderId}`);
    const lock = await lockRef.transaction((current) => {
      if (current && Number(current.expiresAt) > now.getTime()) return;
      return { txnRef, expiresAt: expires.getTime() };
    });
    if (!lock.committed) {
      throw new HttpsError("failed-precondition", "A VNPay payment is already in progress for this order.");
    }

    const fields: VnPayFields = {
      vnp_Version: "2.1.0",
      vnp_Command: "pay",
      vnp_TmnCode: merchantCode,
      vnp_Amount: String(amountVnd * 100),
      vnp_CurrCode: "VND",
      vnp_TxnRef: txnRef,
      vnp_OrderInfo: "vnpay",
      vnp_OrderType: "other",
      vnp_Locale: "vn",
      vnp_ReturnUrl: returnUrl,
      vnp_IpAddr: "127.0.0.1",
      vnp_CreateDate: formatVnDate(now),
      vnp_ExpireDate: formatVnDate(expires),
    };

    try {
      await db.ref(`vnpayPaymentIntents/${txnRef}`).set({
        uid,
        orderId,
        amountVnd,
        status: "pending",
        createdAt: now.getTime(),
        expiresAt: expires.getTime(),
      });
    } catch (error) {
      await clearPendingOrderLock(orderId, txnRef);
      throw error;
    }

    const signature = signedHash(fields, secret);
    const query = Object.entries(fields)
      .map(([key, value]) => `${key}=${encode(value)}`)
      .join("&");
    return { url: `${paymentUrl}?${query}&vnp_SecureHash=${signature}` };
  },
);

export const verifyVnPayReturn = onCall(
  { secrets: [hashSecret] },
  async (request) => {
    const uid = request.auth?.uid;
    if (!uid) throw new HttpsError("unauthenticated", "Sign in before verifying payment.");

    const secret = hashSecret.value();
    if (!secret) throw new HttpsError("failed-precondition", "VNPay is not configured.");

    const fields = parseReturnUrl((request.data as { returnUrl?: unknown } | null)?.returnUrl);
    if (!verifyHash(fields, secret)) {
      throw new HttpsError("permission-denied", "VNPay signature is invalid.");
    }
    if (
      fields.vnp_TmnCode !== merchantCode ||
      fields.vnp_Command !== "pay" ||
      fields.vnp_CurrCode !== "VND" ||
      fields.vnp_OrderInfo !== "vnpay"
    ) {
      throw new HttpsError("permission-denied", "VNPay transaction details are invalid.");
    }

    const txnRef = fields.vnp_TxnRef ?? "";
    const db = getDatabase();
    const intentRef = db.ref(`vnpayPaymentIntents/${txnRef}`);
    const intentSnapshot = await intentRef.get();
    const intent = intentSnapshot.val() as {
      uid?: unknown;
      orderId?: unknown;
      amountVnd?: unknown;
      status?: unknown;
      expiresAt?: unknown;
    } | null;
    const amountVnd = asPositiveSafeInteger(intent?.amountVnd);
    const expiresAt = asPositiveSafeInteger(intent?.expiresAt);
    if (
      !intent ||
      intent.uid !== uid ||
      typeof intent.orderId !== "string" ||
      !amountVnd ||
      !expiresAt ||
      Date.now() > expiresAt + 5 * 60 * 1000 ||
      fields.vnp_Amount !== String(amountVnd * 100)
    ) {
      throw new HttpsError("permission-denied", "VNPay transaction does not match this order.");
    }

    const responseCode = fields.vnp_ResponseCode ?? "";
    const transactionStatus = fields.vnp_TransactionStatus ?? "";
    const success = responseCode === "00" && transactionStatus === "00";
    const orderRef = db.ref(`orders/${intent.orderId}`);
    if (intent.status === "paid") {
      const orderSnapshot = await orderRef.get();
      const order = orderSnapshot.val() as Record<string, unknown> | null;
      if (
        success &&
        order?.customerId === uid &&
        order?.vnpTxnRef === txnRef &&
        order.vnpTransactionNo === (fields.vnp_TransactionNo ?? "") &&
        Number(order.totalPayment) === amountVnd
      ) {
        await clearPendingOrderLock(intent.orderId, txnRef);
        return {
          success: true,
          responseCode,
          transactionStatus,
          txnRef,
          transactionNo: fields.vnp_TransactionNo ?? "",
          amountVnd,
          bankCode: fields.vnp_BankCode ?? "",
          payDate: fields.vnp_PayDate ?? "",
        };
      }
      throw new HttpsError("failed-precondition", "Payment intent is already used.");
    }
    if (intent.status !== "pending") {
      throw new HttpsError("failed-precondition", "Payment intent is no longer active.");
    }

    if (!success) {
      await intentRef.update({ status: "failed" });
      await clearPendingOrderLock(intent.orderId, txnRef);
      return {
        success: false,
        responseCode,
        transactionStatus,
        txnRef,
        transactionNo: fields.vnp_TransactionNo ?? "",
        amountVnd,
        bankCode: fields.vnp_BankCode ?? "",
        payDate: fields.vnp_PayDate ?? "",
      };
    }

    const transaction = await orderRef.transaction((order) => {
      if (
        !order ||
        order.customerId !== uid ||
        order.paymentMethod !== "VNPAY" ||
        (order.status !== "PENDING" && order.orderStatus !== "PENDING") ||
        Number(order.total) !== amountVnd ||
        Number(order.totalPayment) !== 0
      ) {
        return;
      }
      return {
        ...order,
        totalPayment: amountVnd,
        vnpTransactionNo: fields.vnp_TransactionNo ?? "",
        vnpBankCode: fields.vnp_BankCode ?? "",
        vnpPayDate: fields.vnp_PayDate ?? "",
        vnpTxnRef: txnRef,
        paidAt: Date.now(),
        updatedAt: Date.now(),
      };
    });

    if (!transaction.committed) {
      const orderSnapshot = await orderRef.get();
      const order = orderSnapshot.val() as Record<string, unknown> | null;
      if (
        order?.customerId !== uid ||
        order.vnpTxnRef !== txnRef ||
        order.vnpTransactionNo !== (fields.vnp_TransactionNo ?? "") ||
        Number(order.totalPayment) !== amountVnd
      ) {
        throw new HttpsError("failed-precondition", "Order is no longer awaiting payment.");
      }
    }
    await intentRef.update({ status: "paid", transactionNo: fields.vnp_TransactionNo ?? "" });
    await clearPendingOrderLock(intent.orderId, txnRef);

    return {
      success: true,
      responseCode,
      transactionStatus,
      txnRef,
      transactionNo: fields.vnp_TransactionNo ?? "",
      amountVnd,
      bankCode: fields.vnp_BankCode ?? "",
      payDate: fields.vnp_PayDate ?? "",
    };
  },
);
