package app.aaps.pump.omnipod.common.bledriver.comm

object O5IdRotation {

    private const val CONTROLLER_ID_BIT_MASK: Long = 0b11L

    fun controllerIdForPodId(podId: Long): Long =
        podId and (CONTROLLER_ID_BIT_MASK.inv())

    fun firstPodId(controllerId: Long): Long =
        controllerIdForPodId(controllerId) + 1

    fun nextPodId(lastPodId: Long): Long {
        val low2Bits = lastPodId and CONTROLLER_ID_BIT_MASK
        return if (low2Bits == CONTROLLER_ID_BIT_MASK) {
            // wrapped: start over at base + 1
            controllerIdForPodId(lastPodId) + 1
        } else {
            lastPodId + 1
        }
    }
}
