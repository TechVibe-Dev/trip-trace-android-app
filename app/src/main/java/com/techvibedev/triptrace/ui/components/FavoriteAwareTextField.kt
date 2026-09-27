@file:OptIn(ExperimentalMaterial3Api::class)

package com.techvibedev.triptrace.ui.components

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import com.techvibedev.triptrace.data.model.FavoritePlaceResponse

// A text field that offers the user's saved favorites (android#81) two
// ways: typing filters the list live (shown under the field while it has
// focus), and the heart icon opens the full list regardless of what's
// typed, or even with the field empty. Used for origin, destination, and
// stops in Create trip — one field, three callers, same behavior.
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
    var isFocused by remember { mutableStateOf(false) }
    // Separate from typeahead — the heart forces the full list open even
    // with no text and no focus-driven match, e.g. tapping it as the very
    // first action on an empty field.
    var showAllFavorites by remember { mutableStateOf(false) }

    val typeaheadMatches = remember(value, favorites) {
        if (value.isBlank()) favorites else favorites.filter { it.name.contains(value, ignoreCase = true) }
    }
    val menuItems = if (showAllFavorites) favorites else typeaheadMatches
    val expanded = (showAllFavorites || (isFocused && typeaheadMatches.isNotEmpty())) && menuItems.isNotEmpty()

    fun selectFavorite(favorite: FavoritePlaceResponse) {
        onFavoriteSelected(favorite)
        showAllFavorites = false
    }

    Column {
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
                        onClick = { showAllFavorites = !showAllFavorites },
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
            modifier = modifier
                .fillMaxWidth()
                .onFocusChanged { focusState -> isFocused = focusState.isFocused },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { showAllFavorites = false },
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

@Composable
private fun Column(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Column(content = content)
}
