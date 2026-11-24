package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Factory
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.PartyEntity
import com.example.ui.components.rememberAppHaptics
import com.example.ui.theme.LocalLedgerColors

@Composable
fun OnboardingScreen(
    onComplete: (companyName: String, ownerName: String, firstParty: PartyEntity?) -> Unit
) {
    val ledgerColors = LocalLedgerColors.current
    val haptics = rememberAppHaptics()
    var step by remember { mutableIntStateOf(0) }

    var companyName by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf("") }
    var step1Error by remember { mutableStateOf<String?>(null) }

    var partyName by remember { mutableStateOf("") }
    var partyPhone by remember { mutableStateOf("") }
    var openingBalanceText by remember { mutableStateOf("") }
    var step2Error by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        ledgerColors.khataStrip
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top))
            .padding(horizontal = 24.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Factory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            val dot0Width by animateDpAsState(
                targetValue = if (step == 0) 32.dp else 12.dp,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "dot0_width"
            )
            val dot1Width by animateDpAsState(
                targetValue = if (step == 1) 32.dp else 12.dp,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "dot1_width"
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .width(dot0Width)
                        .clip(CircleShape)
                        .background(
                            if (step == 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                )
                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .width(dot1Width)
                        .clip(CircleShape)
                        .background(
                            if (step == 1) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        )
                )
            }

            // Soft border without clipped shadow
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, ledgerColors.borderSubtle),
                shadowElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        val direction = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(animationSpec = tween(320)) { it * direction / 2 } +
                            fadeIn(animationSpec = tween(260))) togetherWith
                            (slideOutHorizontally(animationSpec = tween(260)) { -it * direction / 2 } +
                                fadeOut(animationSpec = tween(200)))
                    },
                    label = "onboarding_steps"
                ) { currentStep ->
                    if (currentStep == 0) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "STEP 1 OF 2",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Your Factory Details",
                                style = MaterialTheme.typography.headlineMedium
                            )
                            Text(
                                text = "This appears on your dashboard and handwritten hisab slips you share with parties.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedTextField(
                                value = companyName,
                                onValueChange = {
                                    companyName = it
                                    step1Error = null
                                },
                                label = { Text("Company / Factory Name") },
                                placeholder = { Text("e.g. Shree Ganesh Engineering") },
                                leadingIcon = { Icon(Icons.Rounded.Business, contentDescription = null) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                shape = MaterialTheme.shapes.medium,
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = ledgerColors.borderSubtle
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("onboarding_company_input")
                            )

                            OutlinedTextField(
                                value = ownerName,
                                onValueChange = {
                                    ownerName = it
                                    step1Error = null
                                },
                                label = { Text("Owner Name") },
                                placeholder = { Text("e.g. Rajesh Kumar") },
                                leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                shape = MaterialTheme.shapes.medium,
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = ledgerColors.borderSubtle
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("onboarding_owner_input")
                            )

                            if (step1Error != null) {
                                Text(
                                    text = step1Error!!,
                                    color = ledgerColors.dangerRed,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Spacer(Modifier.height(4.dp))

                            Button(
                                onClick = {
                                    if (companyName.trim().isEmpty()) {
                                        haptics.reject()
                                        step1Error = "Please enter your company name"
                                        return@Button
                                    }
                                    if (ownerName.trim().isEmpty()) {
                                        haptics.reject()
                                        step1Error = "Please enter the owner name"
                                        return@Button
                                    }
                                    haptics.click()
                                    step = 1
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("onboarding_next_button")
                            ) {
                                Text("Continue")
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "STEP 2 OF 2",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(
                                    onClick = {
                                        haptics.click()
                                        onComplete(companyName.trim(), ownerName.trim(), null)
                                    },
                                    modifier = Modifier.testTag("onboarding_skip_button")
                                ) {
                                    Text("Skip for Now")
                                }
                            }

                            Text(
                                text = "Add Your First Party",
                                style = MaterialTheme.typography.headlineMedium
                            )
                            Text(
                                text = "Start your register by adding a client (optional—you can always add or skip).",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedTextField(
                                value = partyName,
                                onValueChange = {
                                    partyName = it
                                    step2Error = null
                                },
                                label = { Text("Party / Client Name") },
                                placeholder = { Text("e.g. Lucky Pvt. Limited") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                shape = MaterialTheme.shapes.medium,
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = ledgerColors.borderSubtle
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("onboarding_party_input")
                            )

                            OutlinedTextField(
                                value = partyPhone,
                                onValueChange = { partyPhone = it },
                                label = { Text("Phone / WhatsApp (Optional)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                shape = MaterialTheme.shapes.medium,
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = ledgerColors.borderSubtle
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = openingBalanceText,
                                onValueChange = { input ->
                                    if (input.isEmpty() || input == "-" || input.matches(Regex("^-?\\d*\\.?\\d{0,2}$"))) {
                                        openingBalanceText = input
                                    }
                                },
                                label = { Text("Previous Balance ₹ (Optional)") },
                                placeholder = { Text("0") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = MaterialTheme.shapes.medium,
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = ledgerColors.borderSubtle
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (step2Error != null) {
                                Text(
                                    text = step2Error!!,
                                    color = ledgerColors.dangerRed,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        haptics.tick()
                                        step = 0
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.height(52.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                                Button(
                                    onClick = {
                                        val trimmedParty = partyName.trim()
                                        if (trimmedParty.isEmpty()) {
                                            haptics.reject()
                                            step2Error = "Enter a party name or tap Skip for Now"
                                            return@Button
                                        }
                                        val party = PartyEntity(
                                            name = trimmedParty,
                                            phone = partyPhone.trim(),
                                            openingBalance = openingBalanceText.toDoubleOrNull() ?: 0.0
                                        )
                                        haptics.confirm()
                                        onComplete(companyName.trim(), ownerName.trim(), party)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .testTag("onboarding_finish_button")
                                ) {
                                    Text("Add & Enter App")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
