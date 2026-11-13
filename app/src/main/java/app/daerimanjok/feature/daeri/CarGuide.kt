package app.daerimanjok.feature.daeri

import androidx.annotation.StringRes
import app.daerimanjok.R

/** Car models we have a control guide for, with the name shown to the driver. */
enum class CarModel(@StringRes val displayName: Int) { GRANDEUR(R.string.car_model_grandeur) }

/** The controls shown in the driver briefing (S2), in tab order. */
enum class Control { GEAR, PARKING_BRAKE, SEAT }

/**
 * One tab of the briefing: a picture of the control, where it is and how to use it.
 * [picture] is a drawable name, looked up at runtime: the owner's-manual pictures are not in the
 * public repo (copyright), so a build without them shows a placeholder instead.
 */
data class ControlTip(
    val control: Control,
    @StringRes val tab: Int,
    @StringRes val where: Int,
    @StringRes val how: Int,
    val picture: String? = null,
)

object CarGuide {

    /**
     * Car model from the VIN. The Vehicle SDK has no model name, only the VIN; the model is
     * encoded in characters 4–5 (Hyundai/Kia/Genesis vehicle line).
     *
     * [modelCodes] maps WMI + line code to a model. Only codes checked against a real VIN go
     * in; anything unknown (including the emulator, whose VIN is not a real car's) uses the
     * default guide, which for the demo is the new Grandeur.
     */
    fun modelFromVin(vin: String?, modelCodes: Map<String, CarModel> = MODEL_CODES): CarModel {
        val v = vin?.trim()?.uppercase()?.takeIf { it.length == 17 } ?: return DEFAULT_MODEL
        return modelCodes[v.substring(0, 5)] ?: DEFAULT_MODEL
    }

    fun tips(model: CarModel): List<ControlTip> = when (model) {
        CarModel.GRANDEUR -> GRANDEUR
    }

    val DEFAULT_MODEL = CarModel.GRANDEUR

    /** Filled in as VINs are confirmed on real cars, e.g. "KMH??" to [CarModel.GRANDEUR]. */
    private val MODEL_CODES = emptyMap<String, CarModel>()

    // Wording follows the UI prototype. Check each tip against the real car before release.
    private val GRANDEUR = listOf(
        ControlTip(Control.GEAR, R.string.tip_gear_tab, R.string.tip_gear_where, R.string.tip_gear_how, "tip_gear"),
        ControlTip(Control.PARKING_BRAKE, R.string.tip_brake_tab, R.string.tip_brake_where, R.string.tip_brake_how, "tip_parking_brake"),
        ControlTip(Control.SEAT, R.string.tip_seat_tab, R.string.tip_seat_where, R.string.tip_seat_how, "tip_seat"),
    )
}
