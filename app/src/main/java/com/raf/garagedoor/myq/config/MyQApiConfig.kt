package com.raf.garagedoor.myq.config

/**
 * Every value myQ might change in a future app release lives here, so a break can be fixed by
 * updating one place (and later by a remotely fetched override) instead of hunting through code.
 *
 * Values originate from the MIT-licensed bvdcode/myq-home-assistant project (see THIRD_PARTY_NOTICES.md).
 */
data class MyQApiConfig(
    val identityBaseUrl: String = "https://partner-identity.myq-cloud.com",
    val accountsBaseUrl: String = "https://accounts.myq-cloud.com",
    val devicesBaseUrl: String = "https://devices.myq-cloud.com",
    val garageDevicesBaseUrl: String = "https://account-devices-gdo.myq-cloud.com",
    val firebaseAppCheckBaseUrl: String = "https://firebaseappcheck.googleapis.com",
    val oauthClientId: String = "ANDROID_CGI_MYQ",
    val oauthRedirectUri: String = "com.myqops://android",
    val oauthScope: String = "MyQ_Residential offline_access",
    val appVersion: String = "5.243.1.73243",
    val apiUserAgent: String = "sdk_gphone_x86/Android 11",
    val brandId: String = "1",
    val appCheck: AppCheckProfileSecret = AppCheckProfileSecret.BUNDLED,
)

/** XOR-masked Firebase App Check registration used to mint the token myQ requires at sign-in. */
data class AppCheckProfileSecret(
    val maskHex: String,
    val prefixHex: String,
    val bodyHex: String,
    val suffixHex: String,
) {
    override fun toString(): String = "AppCheckProfileSecret(masked)"

    companion object {
        val BUNDLED = AppCheckProfileSecret(
            maskHex = "4bb749f64cade97398ddde0aeaf3d1eb16f43691f5fd3829cd341d764a3b567c",
            prefixHex = "26ce38db38df881debb4aa63859dfc9f73874291c4c7091ff400244f7203664472837397" +
                "22c99b1cf1b9e43bd8c3e6d2209204f3c0980c1dae552a760b722c1d18ce0daf3bc9a331",
            bodyHex = "caade842d9a6b999558403ddb2a40071996457317d53021908d03ef67e98a843aa9f9c3f" +
                "c7c7e1dd22d902a4c0c81510f905295b0c08624872f37cb379e8de4698beb167c490b98a",
            suffixHex = "7b9653e3999c5147e355731238543f1865db209038c08800ecb8ac24878aa0eb729504f3" +
                "919c0f19a8512517730b604e2f877ec02ecc8b16aee8e738de90b08d2f950ef3ccc55d10",
        )
    }
}
