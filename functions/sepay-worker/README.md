# SePay webhook deployment

## Where the processing code and configuration live

- `src/index.js`: Worker endpoint; validates incoming/outgoing webhooks, matches store/order/amount, and updates Firebase. Incoming transfers are recorded as payments; outgoing transfers only confirm a refund already sent by the merchant.
- `test/index.test.js`: mocked Worker tests for payment and refund reconciliation. Run with `npm test`.
- `wrangler.toml`: Worker name, entry point, runtime date, Firebase database URL, and observability setting. Do not keep production secrets in this tracked file.
- Cloudflare Secrets: `SEPAY_WEBHOOK_SECRETS_JSON`, optional `SEPAY_REFUND_WEBHOOK_SECRETS_JSON`, and `FIREBASE_SERVICE_ACCOUNT_JSON`; the Worker reads these values from its `env`.
- SePay Dashboard: webhook URL, `storeId`, HMAC-SHA256 mode, and incoming/outgoing event selection are configured per store, not in the Android app.

The Worker confirms incoming payments and outgoing manual refunds by the order ID in the transfer content or code. Both flows also require the connected store account, store, and exact amount to match Firebase. Refund transfers must use the order ID; separate refund codes are not accepted. Keep both credentials below out of the repository and Android app.

1. In `functions/sepay-worker`, install dependencies and set Cloudflare secrets:

   ```powershell
   npm install
   npx wrangler secret put SEPAY_WEBHOOK_SECRETS_JSON
   npx wrangler secret put SEPAY_REFUND_WEBHOOK_SECRETS_JSON
   npx wrangler secret put FIREBASE_SERVICE_ACCOUNT_JSON
   ```

   `SEPAY_WEBHOOK_SECRETS_JSON` maps each Firebase store ID to the HMAC-SHA256 secret used by its incoming-payment webhook. Refunds use the same secret if the store has one shared secret; configure optional `SEPAY_REFUND_WEBHOOK_SECRETS_JSON` only when the outgoing-refund webhook has a different secret. Each value is a JSON object such as `{"storeId1":"secret-from-sepay"}`. `FIREBASE_SERVICE_ACCOUNT_JSON` is the complete JSON key for a Firebase service account with Realtime Database access. The Worker requests both the `userinfo.email` and `firebase.database` OAuth scopes. `FIREBASE_DATABASE_URL` is a non-secret Worker variable already set in `wrangler.toml`.

2. Deploy the Worker:

   ```powershell
   npm run deploy
   ```

3. Publish the repository's `database.rules.json` to the Firebase Realtime Database. Without these rules, a signed-in app user could alter payment state directly.

4. For each store, configure SePay webhooks with `POST`, HMAC-SHA256, both **incoming** and **outgoing** transfer events, and the URL `https://<worker-host>/webhook?storeId=<Firebase-store-id>`. Put the HMAC secret in `SEPAY_WEBHOOK_SECRETS_JSON`. Verify incoming and outgoing test events before relying on live orders.

`BANKING` and `MOMO` orders are reconciled as incoming payments when SePay reports a transfer to the store's connected bank account and the order reference and exact amount match. Transfers sent directly to a MoMo wallet are not visible to SePay and cannot be auto-confirmed by this Worker. Payment and refund status plus the SePay transaction IDs are saved directly on each order; the Worker does not create separate transaction-history records. Customer-provided refund bank accounts remain in the access-restricted `refund_payment_details` node, which the app needs to generate the refund QR. For a paid order canceled by the store, the merchant manually transfers the exact refund amount from the SePay-connected store account and must include the order ID in the transfer description. The outgoing webhook marks the order `REFUNDED` only when the account, order ID, store, pending refund, and amount match. The merchant must still verify the beneficiary before sending: the SePay webhook payload does not include the beneficiary account number, so matching proves a debit with that reference and amount, not who received it.

Run local Worker tests with `npm test`.
