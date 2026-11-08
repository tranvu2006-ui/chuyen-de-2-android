# Supabase email OTP setup

Customer and internal registration use a six-digit email OTP (`type=email`). For an existing Firebase account whose email is not verified, the first login now also sends and verifies a Supabase OTP instead of sending a Firebase verification link. Set the Supabase email OTP length to 6 so the emailed code matches the app input. After verification, registration creates a Firebase Auth account or login continues with the existing Firebase account. Firebase remains the app's sign-in and Realtime Database provider.

## Create and configure a Supabase project

1. Create a Supabase project and open **Project Settings → API**. Copy the Project URL and the publishable/anon key. Do not use a `service_role` or secret key in the Android app.
2. In **Authentication → Providers → Email**, enable email authentication and keep email confirmations enabled.
3. In **Authentication → Email Templates → Magic Link**, edit the message to show the code instead of relying on a clickable link, for example:

   ```html
   <h2>Oishi Food email verification</h2>
   <p>Your verification code is: <strong>{{ .Token }}</strong></p>
   <p>This code expires soon. Do not share it with anyone.</p>
   ```

4. Configure custom SMTP at **Authentication → Notifications → SMTP Settings** for real recipient addresses. Supabase's default SMTP is restricted to project team addresses and currently limited to about two emails per hour. Resend can be used after its sending domain is verified:
   - Host: `smtp.resend.com`
   - Port: `465`
   - Username: `resend`
   - Password: the Resend API key (enter only in the Supabase dashboard)
   - Sender: an address on the verified domain, such as `no-reply@oishifood.vn`

   Resend's free plan has sending quotas; it is not unlimited. Never put the Resend API key or Supabase `service_role` key in the app.
## Configure the Android build

`core/build.gradle.kts` đọc `supabaseUrl` và `supabaseAnonKey` từ Gradle properties rồi tạo `BuildConfig.SUPABASE_URL`/`SUPABASE_ANON_KEY`. `EmailOtpApi.kt` dùng chúng để gọi `/auth/v1/otp` (gửi mã) và `/auth/v1/verify` (xác minh mã). `AuthRepository.kt` gọi API này trước khi tạo tài khoản Firebase.

The repository includes the Supabase project URL and its public publishable key as build defaults, so a normal clone does not need extra Gradle configuration. To override them for a different Supabase project, add these properties to `%USERPROFILE%\.gradle\gradle.properties`:

```properties
supabaseUrl=https://YOUR_PROJECT_REF.supabase.co
supabaseAnonKey=YOUR_SUPABASE_PUBLISHABLE_OR_ANON_KEY
```

The publishable/anon key is designed for client use; access to Supabase Auth must be protected by the project's Auth settings and provider rate limits. Build both apps after setting the properties. If the file does not exist, create its parent directory and file first.

## Registration behavior and security boundary

Email OTP verification gates the normal in-app registration flow. The resulting account is registered in Firebase so existing Firebase login and Realtime Database code continue to work. The app does not use a Supabase session for Firebase Database access.

Because Firebase account creation is performed by the Android client, this integration is a client-side registration gate, not a server-enforced link between the two identity providers. A user who directly calls Firebase's public sign-up API outside the app may bypass the screen flow. Enforcing the OTP requirement against direct API calls requires trusted server-side verification or using Supabase as the application's primary identity provider with an appropriate Firebase access bridge.
