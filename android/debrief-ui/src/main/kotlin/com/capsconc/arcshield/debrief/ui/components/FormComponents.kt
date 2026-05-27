/*
 * Intellectual Property and Trademark Notice
 *
 * mp4Real™, ArcShield™, CIAER™, and CIAER+™ are trademarks of Capps Consulting
 * Company LLC. The multi-track cyber-physical capture architecture, the
 * application of log-likelihood ratio (LLR) gating to multimodal industrial
 * decision events, and the behavioral codebook discretization methods described
 * in this document are the proprietary intellectual property of Kahn Capps and
 * Capps Consulting Company LLC. Unauthorized commercial use, reproduction, or
 * implementation of the mp4Real™ container architecture or the CIAER™ and CIAER+™
 * schemas without explicit licensing is prohibited. All rights reserved.
 */

package com.capsconc.arcshield.debrief.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// -------------------------------------------------------------------------
// Section header
// -------------------------------------------------------------------------

/**
 * Bold section header used to group related CIAER+ form fields.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text  = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp),
    )
}

// -------------------------------------------------------------------------
// Labeled text field
// -------------------------------------------------------------------------

/**
 * Labeled [OutlinedTextField] with optional required indicator.
 *
 * [minLines] / [maxLines] control the vertical size. For free-text annotations
 * like causal hypothesis, use minLines = 3.
 */
@Composable
fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    required: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 5,
    modifier: Modifier = Modifier,
) {
    val labelText = if (required) "$label *" else label
    OutlinedTextField(
        value          = value,
        onValueChange  = onValueChange,
        label          = { Text(labelText) },
        minLines       = minLines,
        maxLines       = maxLines,
        modifier       = modifier.fillMaxWidth(),
    )
}

// -------------------------------------------------------------------------
// Labeled dropdown (exposed dropdown menu)
// -------------------------------------------------------------------------

/**
 * Labeled exposed dropdown menu for selecting from a fixed [options] list.
 *
 * Uses [ExposedDropdownMenuBox] so the selected value is visible without opening the menu.
 * A null [selected] value renders as an empty selection with a prompt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabeledDropdown(
    label: String,
    options: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    required: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val labelText = if (required) "$label *" else label
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded         = expanded,
        onExpandedChange = { expanded = it },
        modifier         = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value            = selected ?: "",
            onValueChange    = {},
            readOnly         = true,
            label            = { Text(labelText) },
            trailingIcon     = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors           = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier         = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text    = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

// -------------------------------------------------------------------------
// Lambda chip
// -------------------------------------------------------------------------

/**
 * Colored chip displaying a lambda component value.
 *
 * Color scheme reflects diagnostic severity:
 *   - green  (< 0.5):  below baseline — no concern
 *   - yellow (0.5–2.0): elevated — worth noting
 *   - red    (> 2.0):  high — this channel drove the gate event
 */
@Composable
fun LambdaChip(
    label: String,
    value: Float,
    modifier: Modifier = Modifier,
) {
    val containerColor = when {
        value > 2.0f -> Color(0xFFFFCDD2) // red-100
        value > 0.5f -> Color(0xFFFFF9C4) // yellow-100
        else         -> Color(0xFFC8E6C9) // green-100
    }
    val contentColor = when {
        value > 2.0f -> Color(0xFFB71C1C) // red-900
        value > 0.5f -> Color(0xFFF57F17) // amber-900
        else         -> Color(0xFF1B5E20) // green-900
    }

    SuggestionChip(
        onClick  = {},
        label    = { Text("$label: ${"%.2f".format(value)}") },
        colors   = SuggestionChipDefaults.suggestionChipColors(
            containerColor = containerColor,
            labelColor     = contentColor,
        ),
        modifier = modifier,
    )
}
