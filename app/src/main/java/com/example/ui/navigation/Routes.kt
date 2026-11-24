package com.example.ui.navigation

object Routes {
    const val DASHBOARD = "dashboard"
    const val REGISTER = "register"
    const val PARTIES = "parties"
    const val PAYMENTS = "payments"
    const val PARTY_DETAIL_BASE = "party_detail"
    const val PARTY_DETAIL = "party_detail/{partyId}"
    const val HISAB_SLIP_BASE = "hisab_slip"
    const val HISAB_SLIP = "hisab_slip/{hisabId}"
    const val SHARED_SLIPS = "shared_slips"

    fun partyDetail(partyId: Long): String = "$PARTY_DETAIL_BASE/$partyId"

    fun hisabSlip(hisabId: Long): String = "$HISAB_SLIP_BASE/$hisabId"
}
