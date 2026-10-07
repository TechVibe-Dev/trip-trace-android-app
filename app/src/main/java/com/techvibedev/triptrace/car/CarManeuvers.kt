package com.techvibedev.triptrace.car

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.car.app.model.CarIcon
import androidx.car.app.navigation.model.Maneuver
import androidx.core.graphics.drawable.IconCompat
import com.techvibedev.triptrace.R

// Translates Google Routes' maneuver names (the same values ManeuverIcon
// draws on the phone) into the Car App Library's maneuver types, each with
// its own icon. Anything unrecognized is shown as "straight" rather than
// failing, same fallback as the phone.
internal fun carManeuver(context: Context, googleManeuver: String): Maneuver {
    val (type, icon) = when (googleManeuver) {
        "TURN_SLIGHT_LEFT" -> Maneuver.TYPE_TURN_SLIGHT_LEFT to R.drawable.ic_car_maneuver_slight_left
        "TURN_LEFT" -> Maneuver.TYPE_TURN_NORMAL_LEFT to R.drawable.ic_car_maneuver_turn_left
        "TURN_SHARP_LEFT" -> Maneuver.TYPE_TURN_SHARP_LEFT to R.drawable.ic_car_maneuver_sharp_left
        "TURN_SLIGHT_RIGHT" -> Maneuver.TYPE_TURN_SLIGHT_RIGHT to R.drawable.ic_car_maneuver_slight_right
        "TURN_RIGHT" -> Maneuver.TYPE_TURN_NORMAL_RIGHT to R.drawable.ic_car_maneuver_turn_right
        "TURN_SHARP_RIGHT" -> Maneuver.TYPE_TURN_SHARP_RIGHT to R.drawable.ic_car_maneuver_sharp_right
        "UTURN_LEFT" -> Maneuver.TYPE_U_TURN_LEFT to R.drawable.ic_car_maneuver_uturn_left
        "UTURN_RIGHT" -> Maneuver.TYPE_U_TURN_RIGHT to R.drawable.ic_car_maneuver_uturn_right
        "FORK_LEFT" -> Maneuver.TYPE_FORK_LEFT to R.drawable.ic_car_maneuver_slight_left
        "FORK_RIGHT" -> Maneuver.TYPE_FORK_RIGHT to R.drawable.ic_car_maneuver_slight_right
        "RAMP_LEFT" -> Maneuver.TYPE_KEEP_LEFT to R.drawable.ic_car_maneuver_slight_left
        "RAMP_RIGHT" -> Maneuver.TYPE_KEEP_RIGHT to R.drawable.ic_car_maneuver_slight_right
        "MERGE" -> Maneuver.TYPE_MERGE_SIDE_UNSPECIFIED to R.drawable.ic_car_maneuver_straight
        // Counterclockwise: traffic drives on the right where this app is
        // used. The "enter and exit" types would need the exit number,
        // which Google's step doesn't give as a separate field.
        "ROUNDABOUT_LEFT", "ROUNDABOUT_RIGHT" ->
            Maneuver.TYPE_ROUNDABOUT_ENTER_CCW to R.drawable.ic_car_maneuver_roundabout
        "DEPART" -> Maneuver.TYPE_DEPART to R.drawable.ic_car_maneuver_straight
        "NAME_CHANGE" -> Maneuver.TYPE_NAME_CHANGE to R.drawable.ic_car_maneuver_straight
        "FERRY" -> Maneuver.TYPE_FERRY_BOAT to R.drawable.ic_car_maneuver_straight
        "FERRY_TRAIN" -> Maneuver.TYPE_FERRY_TRAIN to R.drawable.ic_car_maneuver_straight
        else -> Maneuver.TYPE_STRAIGHT to R.drawable.ic_car_maneuver_straight
    }
    return Maneuver.Builder(type).setIcon(carIcon(context, icon)).build()
}

internal fun carIcon(context: Context, @DrawableRes resId: Int): CarIcon =
    CarIcon.Builder(IconCompat.createWithResource(context, resId)).build()
