package com.giraffe.mizanapp.domain.identity

/**
 * The two sign-out paths, deliberately different in weight.
 *
 * Neither removes anything from the account (FR-007d).
 */
enum class SignOutMode {

    /** Plain sign-out. Every record stays on the device, fully usable (FR-007a). */
    KEEP_LOCAL_RECORDS,

    /**
     * The shared-device path (FR-007b). Requires a confirmation naming what is
     * about to be removed, and is the only route in the product to deleting a
     * local record — apart from the account switch FR-013a authorises, which
     * uses the same confirmation.
     */
    REMOVE_LOCAL_RECORDS,
}
