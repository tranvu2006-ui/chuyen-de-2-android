# SePay webhook deployment

The Worker confirms only incoming transfers whose receiving account, unique order reference, store, payment method, and amount match Firebase. Keep both credentials below out of the repository and Android app.

1. In `functions/sepay-worker`, install dependencies and set Cloudflare secrets:

   ```powershell
   npm install
   npx wrangler secret put SEPAY_WEBHOOK_SECRETS_JSON
   npx wrangler secret put FIREBASE_SERVICE_ACCOUNT_JSON
   ```

   `SEPAY_WEBHOOK_SECRETS_JSON` is a JSON object mapping each Firebase store ID to that store's SePay HMAC-SHA256 secret, for example `{"storeId1":"secret-from-sepay-1","storeId2":"secret-from-sepay-2"}`. Use the same store IDs in the webhook URL. `FIREBASE_SERVICE_ACCOUNT_JSON` is the complete JSON key for a Firebase service account with Realtime Database access. `FIREBASE_DATABASE_URL` is a non-secret Worker variable already set in `wrangler.toml`.

2. Deploy the Worker:

   ```powershell
   npm run deploy
   ```

3. Publish the repository's `database.rules.json` to the Firebase Realtime Database. Without these rules, a signed-in app user could alter payment state directly.

4. For each store, configure a SePay webhook with `POST`, HMAC-SHA256, and the URL `https://<worker-host>/webhook?storeId=<Firebase-store-id>`. Put its unique HMAC secret in the corresponding entry in `SEPAY_WEBHOOK_SECRETS_JSON`. Verify a test transfer from SePay before relying on live orders.

The bank-transfer QR puts the order's `paymentReference` in the transfer description. SePay must preserve that reference in its webhook `content` or `code` field. Orders created before this change have no reference and cannot be automatically reconciled.

Run local Worker tests with `npm test`.
