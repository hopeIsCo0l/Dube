package com.sook.dubedebter

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class CoachMarkStep(
    val titleRes: Int,
    val descRes: Int,
    val alignment: Alignment
)

@Composable
fun CoachMarkOverlay(
    stepIndex: Int,
    onNextStep: () -> Unit,
    onSkipTour: () -> Unit
) {
    val steps = remember {
        listOf(
            CoachMarkStep(R.string.coach_mark_title_1, R.string.coach_mark_desc_1, Alignment.Center),
            CoachMarkStep(R.string.coach_mark_title_2, R.string.coach_mark_desc_2, Alignment.BottomCenter),
            CoachMarkStep(R.string.coach_mark_title_3, R.string.coach_mark_desc_3, Alignment.Center),
            CoachMarkStep(R.string.coach_mark_title_4, R.string.coach_mark_desc_4, Alignment.TopCenter)
        )
    }

    if (stepIndex !in steps.indices) return
    val currentStep = steps[stepIndex]

    val transition = rememberInfiniteTransition(label = "SpotlightPulse")
    val pulseScale by transition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* Consume taps outside */ }
    ) {
        // Spotlight pulse ring positioned according to target step
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = currentStep.alignment
        ) {
            Box(
                modifier = Modifier
                    .size(if (stepIndex == 3) 72.dp else 160.dp, 64.dp)
                    .scale(pulseScale)
                    .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            )
        }

        // Spotlight Tooltip Card
        Card(
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(24.dp)
                .align(
                    if (currentStep.alignment == Alignment.TopCenter) Alignment.Center
                    else if (currentStep.alignment == Alignment.BottomCenter) Alignment.TopCenter
                    else Alignment.BottomCenter
                )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = stringResource(R.string.coach_mark_step_format, stepIndex + 1, steps.size),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    TextButton(onClick = onSkipTour) {
                        Text(
                            text = stringResource(R.string.coach_mark_skip),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                Text(
                    text = stringResource(currentStep.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = stringResource(currentStep.descRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onNextStep,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (stepIndex == steps.size - 1) {
                                stringResource(R.string.coach_mark_got_it)
                            } else {
                                stringResource(R.string.coach_mark_next)
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
