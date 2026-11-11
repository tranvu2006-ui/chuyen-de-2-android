package com.chuyen_de_2.foodorder.core.util

/**
 * Danh sách 54 Ngân hàng thành viên VietQR (NAPAS 24/7) và Ví điện tử tại Việt Nam.
 * Cung cấp mã BIN chuẩn để sinh VietQR và danh sách Package Name để lọc thông báo biến động số dư.
 */
object BankData {

    data class SupportedBank(
        val name: String,
        val bin: String,
        val shortName: String = ""
    )

    /**
     * Danh sách toàn bộ các Ngân hàng thành viên VietQR Napas tại Việt Nam.
     */
    val SUPPORTED_BANKS = listOf(
        SupportedBank("Vietcombank (Ngoại Thương)", "970436", "VCB"),
        SupportedBank("MB Bank (Quân Đội)", "970422", "MBBank"),
        SupportedBank("Techcombank (Kỹ Thương)", "970407", "Techcombank"),
        SupportedBank("BIDV (Đầu Tư & Phát Triển)", "970418", "BIDV"),
        SupportedBank("VietinBank (Công Thương)", "970415", "VietinBank"),
        SupportedBank("Agribank (Nông Nghiệp)", "970405", "Agribank"),
        SupportedBank("ACB (Á Châu)", "970416", "ACB"),
        SupportedBank("VPBank (Việt Nam Thịnh Vượng)", "970432", "VPBank"),
        SupportedBank("TPBank (Tiên Phong)", "970423", "TPBank"),
        SupportedBank("Sacombank (Sài Gòn Thương Tín)", "970403", "Sacombank"),
        SupportedBank("HDBank (Phát Triển TP.HCM)", "970437", "HDBank"),
        SupportedBank("VIB (Quốc Tế)", "970441", "VIB"),
        SupportedBank("OCB (Phương Đông)", "970448", "OCB"),
        SupportedBank("MSB (Hàng Hải)", "970426", "MSB"),
        SupportedBank("SHB (Sài Gòn - Hà Nội)", "970443", "SHB"),
        SupportedBank("LPBank (Lộc Phát Việt Nam)", "970449", "LPBank"),
        SupportedBank("SeABank (Đông Nam Á)", "970440", "SeABank"),
        SupportedBank("Eximbank (Xuất Nhập Khẩu)", "970431", "Eximbank"),
        SupportedBank("Nam A Bank (Nam Á)", "970428", "NamABank"),
        SupportedBank("Bac A Bank (Bắc Á)", "970409", "BacABank"),
        SupportedBank("VietCapitalBank / BVBank (Bản Việt)", "970454", "BVBank"),
        SupportedBank("CAKE by VPBank (Ngân hàng số Cake)", "546034", "CAKE"),
        SupportedBank("Timo (Ngân hàng số Timo)", "963388", "Timo"),
        SupportedBank("TNEX (Ngân hàng số TNEX MSB)", "970426", "TNEX"),
        SupportedBank("Liobank (Ngân hàng số Liobank)", "970448", "Liobank"),
        SupportedBank("PVcomBank (Đại Chúng)", "970412", "PVcomBank"),
        SupportedBank("Shinhan Bank (Shinhan Việt Nam)", "970424", "ShinhanBank"),
        SupportedBank("ABBANK (An Bình)", "970425", "ABBANK"),
        SupportedBank("VietABank (Việt Á)", "970427", "VietABank"),
        SupportedBank("VietBank (Việt Nam Thương Tín)", "970433", "VietBank"),
        SupportedBank("BaoViet Bank (Bảo Việt)", "970438", "BaoVietBank"),
        SupportedBank("Saigonbank (Sài Gòn Công Thương)", "970400", "SaigonBank"),
        SupportedBank("PGBank (Thịnh Vượng & Phát Triển)", "970430", "PGBank"),
        SupportedBank("NCB (Quốc Dân)", "970419", "NCB"),
        SupportedBank("SCB (Sài Gòn)", "970429", "SCB"),
        SupportedBank("Kienlongbank (Kiên Long)", "970452", "KienLongBank"),
        SupportedBank("Co-opBank (Hợp Tác Xã)", "970446", "COOPBANK"),
        SupportedBank("CBBank (Xây Dựng)", "970444", "CBBank"),
        SupportedBank("KBank (Kasikornbank)", "668888", "KBank"),
        SupportedBank("Woori Bank (Woori Việt Nam)", "970457", "Woori"),
        SupportedBank("CIMB (CIMB Việt Nam)", "422589", "CIMB"),
        SupportedBank("Indovina Bank (Indovina)", "970434", "IndovinaBank"),
        SupportedBank("Public Bank (Public Bank VN)", "970439", "PublicBank"),
        SupportedBank("Hong Leong Bank", "970442", "HongLeong"),
        SupportedBank("UOB (United Overseas Bank)", "970458", "UOB"),
        SupportedBank("HSBC Việt Nam", "458761", "HSBC"),
        SupportedBank("Standard Chartered", "970410", "StandardChartered"),
        SupportedBank("VRB (Việt - Nga)", "970421", "VRB"),
        SupportedBank("IBK (Công nghiệp Hàn Quốc)", "970455", "IBK"),
        SupportedBank("Kookmin Bank (Hà Nội)", "970462", "KB_HN"),
        SupportedBank("Kookmin Bank (TP.HCM)", "970463", "KB_HCM"),
        SupportedBank("Nonghyup Bank (Hà Nội)", "801011", "Nonghyup"),
        SupportedBank("Ví MoMo", "971025", "MoMo"),
        SupportedBank("Ví ZaloPay", "971101", "ZaloPay")
    )

    /**
     * Danh sách các ứng dụng chat, mạng xã hội bị CHẶN TUYỆT ĐỐI (Blacklist).
     * Ngăn chặn hành vi giả mạo tin nhắn chuyển khoản qua tin nhắn chat.
     */
    val BLACKLISTED_CHAT_PACKAGES = setOf(
        "com.zing.zalo",
        "com.facebook.orca",            // Messenger
        "com.facebook.katana",          // Facebook
        "com.facebook.lite",
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "com.whatsapp",
        "com.whatsapp.w4b",
        "com.instagram.android",
        "com.viber.voip",
        "com.skype.raider",
        "com.discord",
        "com.snapchat.android",
        "com.tiktok.android",
        "com.zhiliaoapp.musically"
    )

    /**
     * Danh sách Package Name chính thức của các ứng dụng Ngân hàng & Ví điện tử tại Việt Nam.
     */
    val OFFICIAL_BANK_PACKAGES = setOf(
        // Big 4
        "com.VCB",                              // Vietcombank
        "com.mbmobile",                         // MB Bank
        "com.mb.bank",
        "com.techcombank.mobile",               // Techcombank
        "vn.com.techcombank.bb.app",
        "com.vnpay.bidv",                       // BIDV SmartOTP / BIDV SmartBanking
        "com.vietinbank.ipay",                  // VietinBank iPay
        "com.vnpay.Agribank3g",                 // Agribank E-Mobile Banking

        // TMCP Lớn
        "mobile.acb.com.vn",                    // ACB ONE
        "com.acb.one",
        "com.vnpay.vpbankonline",               // VPBank NEO
        "com.vpbank.neo",
        "com.tpb.mb.gprsandroid",               // TPBank Mobile
        "com.vnpay.sacombank",                  // Sacombank mBanking / Pay
        "com.sacombank.mplus",
        "com.vnpay.hdbank",                     // HDBank
        "com.vib.vibmobile",                    // MyVIB
        "com.ocb.omni.mobile",                  // OCB OMNI
        "com.vnpay.msb",                        // MSB mBank
        "com.vnpay.shb",                        // SHB Mobile
        "com.vnpay.seabank",                    // SeAMobile
        "vn.lpb.lienviet24h",                   // LPBank
        "com.vnpay.eximbank",                   // Eximbank Mobile Banking
        "com.vnpay.namabank",                   // Nam A Bank Open Sky
        "com.vnpay.bacabank",                   // Bac A Bank
        "com.vnpay.pvcombank",                  // PVcomBank
        "com.vnpay.scb",                        // SCB
        "com.vnpay.vietabank",                  // VietABank
        "com.vnpay.vietbank",                   // VietBank
        "com.vnpay.kienlongbank",               // KienlongBank Plus
        "com.vnpay.abbank",                     // AB Ditizen
        "com.vnpay.baovietbank",                // BAOVIET Smart
        "com.vnpay.saigonbank",                 // SAIGONBANK Smart Banking
        "com.vnpay.pgbank",                     // PG Bank Flexicash
        "com.vnpay.ncb",                        // iziMobile NCB
        "com.vnpay.coopbank",                   // Co-opBank Mobile Banking

        // Ngân hàng số & Ví điện tử
        "vn.be.cake",                           // Cake by VPBank
        "vn.timo.digitalbank",                  // Timo
        "timo.banviet.vn",
        "vn.tnex.consumer",                     // TNEX
        "vn.com.liobank",                       // Liobank
        "com.mservice.momotransfer",            // MoMo
        "vn.com.vng.zalopay",                   // ZaloPay
        "com.bplus.vtpay",                      // Viettel Money
        "com.airpay",                           // ShopeePay
        "vn.com.vnpt.money",                    // VNPT Money

        // Ngân hàng quốc tế
        "com.shinhan.smartbanking",             // Shinhan SOL Việt Nam
        "com.wooribank.smart.wvn",              // Woori WON Việt Nam
        "com.kasikornbank.kplus.vn",            // K PLUS Việt Nam
        "vn.cimb.octo",                         // OCTO by CIMB
        "com.vnpay.ivb",                        // IVB Mobile Banking
        "com.vnpay.publicbank",                 // Public Bank Mobile
        "com.uob.mighty.vn",                    // UOB TMRW
        "com.hsbc.hsbcvietnam",                 // HSBC Vietnam
        "com.sc.scmobile.vn",                   // SC Mobile VN

        // Debug/Shell testing
        "com.android.shell"
    )

    /**
     * Tên thương hiệu người gửi (Brandname) hợp lệ khi nhận tin nhắn SMS biến động số dư.
     */
    val VALID_BANK_SMS_BRANDNAMES = listOf(
        "VCB", "VIETCOMBANK", "MBBANK", "MB BANK", "TECHCOMBANK", "TCB", "BIDV",
        "VIETINBANK", "CTG", "AGRIBANK", "ACB", "VPBANK", "TPBANK", "SACOMBANK",
        "HDBANK", "VIB", "OCB", "MSB", "SHB", "SEABANK", "LPBANK", "EXIMBANK",
        "NAMABANK", "BACABANK", "BVBANK", "CAKE", "TIMO", "TNEX", "LIOBANK",
        "PVCOMBANK", "SHINHAN", "ABBANK", "VIETABANK", "VIETBANK", "BAOVIET",
        "SAIGONBANK", "PGBANK", "NCB", "SCB", "KIENLONG", "COOPBANK", "KBANK",
        "WOORI", "CIMB", "IVB", "MOMO", "ZALOPAY", "VIETTELMONEY"
    )
}

