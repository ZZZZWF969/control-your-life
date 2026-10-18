package com.dailycontrol.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dailycontrol.app.R
import kotlinx.coroutines.flow.drop
import java.time.LocalTime
import kotlin.math.abs

// 通用时间选择弹窗：双列滚轮（时 0-23 / 分 0-59 逐分钟），2026-10-18 替换旧时钟盘
// 实现：两列 LazyColumn + 官方吸附惯性（SnapFlingBehavior，滑完自动对齐整行），
// 条目首尾循环滚不到头，中心高亮行即选中行；对外回调与旧版完全一致，调用方零改动
@Composable
fun TimePickerDialog(
	initial: LocalTime,
	onConfirm: (LocalTime) -> Unit,
	onDismiss: () -> Unit
) {
	// 起始位置：循环列表的中段 + 当前值，两个方向都足够长；首行索引需扣除顶部填充的 2 行
	val hourState = rememberLazyListState(
		initialFirstVisibleItemIndex = initialTopIndex(initial.hour, HOUR_COUNT)
	)
	val minuteState = rememberLazyListState(
		initialFirstVisibleItemIndex = initialTopIndex(initial.minute, MINUTE_COUNT)
	)

	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(R.string.time_picker_title)) },
		text = {
			Row(
				horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
				modifier = Modifier.fillMaxWidth()
			) {
				WheelColumn(hourState, HOUR_COUNT, R.string.time_picker_hour, Modifier.weight(1f))
				WheelColumn(minuteState, MINUTE_COUNT, R.string.time_picker_minute, Modifier.weight(1f))
			}
		},
		confirmButton = {
			TextButton(onClick = {
				// 取离视口中心最近的一行，再折算回真实值（循环列表原始索引对 count 取模）
				val hour = centeredRawIndex(hourState) % HOUR_COUNT
				val minute = centeredRawIndex(minuteState) % MINUTE_COUNT
				onConfirm(LocalTime.of(hour, minute))
			}) {
				Text(stringResource(R.string.action_confirm))
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(R.string.action_cancel))
			}
		}
	)
}

// 单列滚轮：固定行高、显示 5 行、中心行高亮；上下各垫 2 行填充让首尾条目也能停在中心
@Composable
private fun WheelColumn(
	state: LazyListState,
	count: Int,
	captionRes: Int,
	modifier: Modifier = Modifier
) {
	val haptic = LocalHapticFeedback.current
	// 滚动跨行时轻触一下，模拟实体滚轮的「咔哒」手感
	LaunchedEffect(state) {
		snapshotFlow { state.firstVisibleItemIndex }
			.drop(1)
			.collect { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
	}
	// 中心行索引做派生状态：只在跨行时重算，高亮行随之移动
	val centered by remember { derivedStateOf { centeredRawIndex(state) } }

	Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
		Text(
			text = stringResource(captionRes),
			style = MaterialTheme.typography.labelMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
		Box(modifier = Modifier.fillMaxWidth().height(ROW_HEIGHT * 5)) {
			// 中心高亮带：先声明画在列表下层，文字压在其上
			Box(
				modifier = Modifier
					.align(Alignment.Center)
					.fillMaxWidth()
					.height(ROW_HEIGHT)
					.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp))
			)
			LazyColumn(
				state = state,
				flingBehavior = rememberSnapFlingBehavior(lazyListState = state),
				contentPadding = PaddingValues(vertical = ROW_HEIGHT * 2),
				modifier = Modifier.fillMaxWidth()
			) {
				items(count * REPETITIONS) { raw ->
					Box(
						modifier = Modifier.fillMaxWidth().height(ROW_HEIGHT),
						contentAlignment = Alignment.Center
					) {
						Text(
							text = (raw % count).toString().padStart(2, '0'),
							style = MaterialTheme.typography.titleLarge,
							color = if (raw == centered) {
								MaterialTheme.colorScheme.onSecondaryContainer
							} else {
								MaterialTheme.colorScheme.onSurfaceVariant
							}
						)
					}
				}
			}
		}
	}
}

// 从布局信息找中心行：用「可见条目自身跨度的中点」定位，
// 不依赖 viewportStartOffset——它带 contentPadding 时是负值（坐标系约定），
// 直接取视口中点会恒差出顶部填充行数（实测曾稳定选错 2 行）
private fun centeredRawIndex(state: LazyListState): Int {
	val items = state.layoutInfo.visibleItemsInfo
	if (items.isEmpty()) {
		return 0
	}
	val spanCenter = (items.first().offset + items.last().offset + items.last().size) / 2
	return items.minByOrNull { abs(it.offset + it.size / 2 - spanCenter) }?.index ?: 0
}

// 起始索引：中段循环 + 目标值即可——吸附锚点本身就是「顶部内边距槽位」（即中心行），
// 目标值作为首可见条目会被摆到中心；此前误减 2 行导致开窗整体偏低两行（实测定位）
private fun initialTopIndex(value: Int, count: Int): Int {
	return (REPETITIONS / 2) * count + value
}

	private const val HOUR_COUNT = 24
	private const val MINUTE_COUNT = 60
	// 循环圈数：10 圈（时 240 行 / 分 600 行），配合中段起始，正常使用滚不到头
	private const val REPETITIONS = 10
	private val ROW_HEIGHT = 48.dp
