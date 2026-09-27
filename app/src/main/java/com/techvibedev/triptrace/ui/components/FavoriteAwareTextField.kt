@file:OptIn(ExperimentalMaterial3Api::class)

package com.techvibedev.triptrace.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.techvibedev.triptrace.data.model.FavoritePlaceResponse

// A text field that offers the user's saved favorites (android#81) two
// ways: typing something that matches a favorite's name opens the list of
// matches on its own, and the heart icon opens the full list regardless of
// what's typed. No matching text and no heart tap means no list at all —
// this isn't tied to focus (an earlier version was; see below for why that
// changed). Used for origin, destination, and stops in Create trip — one
// field, three callers, same behavior.
//
// The list renders as a plain inline Surface below the field, not a
// DropdownMenu — DropdownMenu is built on Popup, which can steal focus
// from whatever had it when it appears. That turned out not to be the
// whole story though: the keyboard still dropped after switching to this
// inline version, so the field also explicitly reclaims focus (below)
// whenever the list's visibility changes while the user is mid-typing —
// whatever transient thing is stealing it, forcing it back is a more
// robust fix than chasing the exact mechanism.
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
    val focusRequester = remember { FocusRequester() }

    // menuOpen is the one source of truth for whether the list shows —
    // deliberately not re-derived from focus state, which caused a real
    // freeze bug (see the PR history on this file) back when this used
    // DropdownMenu.
    var menuOpen by remember { mutableStateOf(false) }
    // true = the heart forced the full list open; false = typing drove it.
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

    // Reclaims focus every time the list's visibility flips, but only for
    // the typing-driven case — the heart's full list is meant to be
    // browsed/picked from, not typed into, so it's fine (arguably correct)
    // if that path lets the keyboard drop. requestFocus() on an
    // already-focused field is a harmless no-op, so this doesn't need to
    // check whether focus actually needs reclaiming first.
    LaunchedEffect(menuOpen) {
        if (!showAllFavorites) {
            focusRequester.requestFocus()
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
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
        )
        if (menuOpen && menuItems.isNotEmpty()) {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 3.dp,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    menuItems.forEach { favorite ->
                        Text(
                            text = favorite.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = enabled) { selectFavorite(favorite) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }
}
