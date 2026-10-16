package com.dailycontrol.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dailycontrol.app.R
import com.dailycontrol.app.data.entity.DietRecord
import com.dailycontrol.app.data.entity.MealType
import com.dailycontrol.app.data.entity.NutrientItem
import com.dailycontrol.app.util.TimeUtil

// 各餐别的徽标配色（浅色底 + 深色字，深浅两套主题下都可读）
private data class MealBadgeStyle(val background: Color, val content: Color, val badgeChar: String)

private fun badgeStyleOf(mealType: MealType): MealBadgeStyle {
	return when (mealType) {
		MealType.BREAKFAST -> MealBadgeStyle(Color(0xFFF6E3B4), Color(0xFF6B4E00), "早")
		MealType.LUNCH -> MealBadgeStyle(Color(0xFFC2F0E9), Color(0xFF042F2C), "午")
		MealType.DINNER -> MealBadgeStyle(Color(0xFFC7CBF8), Color(0xFF1B1F65), "晚")
		MealType.SNACK -> MealBadgeStyle(Color(0xFFF8D4DE), Color(0xFF652E3E), "零")
		MealType.SUPPLEMENT -> MealBadgeStyle(Color(0xFFCFEBC4), Color(0xFF1F3D17), "补")
	}
}

// 餐别徽标：圆形底色 + 单字（早/午/晚/零/补），不依赖扩展图标库
@Composable
private fun MealBadge(mealType: MealType) {
	val style = badgeStyleOf(mealType)
	Box(
		modifier = Modifier.size(44.dp).background(style.background, CircleShape),
		contentAlignment = Alignment.Center
	) {
		Text(
			text = style.badgeChar,
			style = MaterialTheme.typography.titleMedium,
			color = style.content
		)
	}
}

// 单条饮食记录的卡片：点击进入编辑，右侧按钮触发删除；营养品会多显示一行营养素摘要
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DietRecordCard(
	record: DietRecord,
	nutrientItems: List<NutrientItem>,
	onClick: () -> Unit,
	onDelete: () -> Unit
) {
	val mealType = MealType.fromStorage(record.mealType)
	Card(
		onClick = onClick,
		modifier = Modifier.fillMaxWidth()
	) {
		Row(
			modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			MealBadge(mealType)
			Spacer(modifier = Modifier.size(14.dp))
			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = record.foodText,
					style = MaterialTheme.typography.titleSmall,
					maxLines = 2,
					overflow = TextOverflow.Ellipsis
				)
				Spacer(modifier = Modifier.height(4.dp))
				Text(
					text = buildString {
						append(stringResource(mealType.labelRes))
						// 三餐显示饭量档位；零食/营养品显示重量或容量
						if (mealType == MealType.SNACK || mealType == MealType.SUPPLEMENT) {
							record.weightText?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
						} else {
							append(" · ").append(record.portionText)
						}
						append(" · ").append(TimeUtil.formatTime(record.eatenAt))
					},
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
				// 营养素摘要：优先显示结构化成分行，兼容旧版自由文本字段
				val nutrientSummary = nutrientItems.joinToString(" · ") { item ->
					(item.name + " " + item.amountText).trim()
				}.ifBlank { record.nutrientText.orEmpty() }
				if (nutrientSummary.isNotBlank()) {
					Spacer(modifier = Modifier.height(2.dp))
					Text(
						text = nutrientSummary,
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.tertiary,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
				}
			}
			IconButton(onClick = onDelete) {
				Icon(
					imageVector = Icons.Outlined.Delete,
					contentDescription = stringResource(R.string.action_delete),
					tint = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}
		}
	}
}
