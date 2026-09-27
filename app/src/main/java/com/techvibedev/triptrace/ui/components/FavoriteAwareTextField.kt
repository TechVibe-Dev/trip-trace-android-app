@file:OptIn(ExperimentalMaterial3Api::class)

package com.techvibedev.triptrace.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.techvibedev.triptrace.data.model.FavoritePlaceResponse

// A text field that offers the user's saved favorites (android#81) two
// ways: typing something that matches a favorite's name opens the list of
// matches on its own, and the heart icon opens the full list regardless of
// what's typed. No matching text and no heart tap means no dropdown at
// all — this isn't tied to focus (an earlier version was; see below for
// why that changed). Used for origin, destination, and stops in Create
// trip — one field, three callers, same behavior.
//
// extraTrailingIcon is a slot for a caller-specific action placed before
// the heart (Create trip uses it for "confirm on map") — kept generic here
// so this component doesn't need to know about map-confirm at all.
@Composable
fun FavoriteAwareTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    favorites: List<FavoritePlaceResponse>,
    onFavoriteSelected: (FavoritePlaceResponse) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    extraTrailingIcon: (@Composable () -> Unit)? = null,
) {
    // menuOpen is the one source of truth for whether the dropdown shows —
    // deliberately NOT re-derived from focus state. An earlier version
    // computed it as "focused AND has a match", which caused a real bug:
    // DropdownMenu's own outside-tap dismiss fires while the field still
    // reports itself focused (the tap gets consumed by the popup before it
    // could move focus anywhere), so that formula recomputed straight back
    // to true on the next frame — the menu re-opened itself the instant it
    // closed, its scrim then ate every further tap, and nothing on screen
    // (including the field itself) could be interacted with without force-
    // closing the app. Tracking this explicitly, only closed by a real
    // dismiss/selection, avoids the loop entirely.
    var menuOpen by remember { mutableStateOf(false) }
    // true = the heart forced the full list open; false = typing drove it.
    // Kept separate from menuOpen so a dismiss can reset both without the
    // two fighting over which list to show while open.
    var showAllFavorites by remember { mutableStateOf(false) }

    val typeaheadMatches = remember(value, favorites) {
        if (value.isBlank()) emptyList() else favorites.filter { it.name.contains(value, ignoreCase = true) }
    }
    val menuItems = if (showAllFavorites) favorites else typeaheadMatches

    // Reacts only to the text actually changing — opens the moment typing
    // produces a match, closes the moment it stops matching (cleared the
    // field, or kept typing past it). Skipped while the heart's full list
    // is showing, so typing during that doesn't fight it closed.
    LaunchedEffect(value, favorites) {
        if (!showAllFavorites) {
            menuOpen = typeaheadMatches.isNotEmpty()
        }
    }

    fun dismissMenu() {
        menuOpen = false
        showAllFavorites = false
    }

    fun selectFavorite(favorite: FavoritePlaceResponse) {
        onFavoriteSelected(favorite)
        dismissMenu()
    }

    // modifier applied to this Column, not directly to the OutlinedTextField
    // below — a caller passing Modifier.weight(1f) from inside a Row (the
    // "Agregar parada" row does) needs that weight on the field's actual
    // direct child in the Row, which is this Column, not a grandchild two
    // levels down. Weight silently does nothing useful applied that deep.
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            enabled = enabled,
            trailingIcon = {
                Row {
                    extraTrailingIcon?.invoke()
                    IconButton(
                        onClick = {
                            if (menuOpen && showAllFavorites) {
                                dismissMenu()
                            } else {
                                showAllFavorites = true
                                menuOpen = true
                            }
                        },
                        enabled = enabled,
                    ) {
                        Icon(
                            imageVector = if (favorites.isEmpty()) {
                                Icons.Filled.FavoriteBorder
                            } else {
                                Icons.Filled.Favorite
                            },
                            contentDescription = "Elegir favorito",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { dismissMenu() },
        ) {
            menuItems.forEach { favorite ->
                DropdownMenuItem(
                    text = { Text(favorite.name) },
                    onClick = { selectFavorite(favorite) },
                )
            }
        }
    }
}
