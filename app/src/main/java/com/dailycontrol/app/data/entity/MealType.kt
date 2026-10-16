package com.dailycontrol.app.data.entity

import androidx.annotation.StringRes
import com.dailycontrol.app.R

// 餐别定义：storageName 是数据库存储值，labelRes 是界面显示文案
enum class MealType(val storageName: String, @StringRes val labelRes: Int) {
	BREAKFAST("breakfast", R.string.meal_breakfast),
	LUNCH("lunch", R.string.meal_lunch),
	DINNER("dinner", R.string.meal_dinner),
	SNACK("snack", R.string.meal_snack),
	SUPPLEMENT("supplement", R.string.meal_supplement);

	companion object {
		// 从数据库存储值还原枚举；无法识别时按零食处理
		fun fromStorage(value: String?): MealType {
			return entries.firstOrNull { it.storageName == value } ?: SNACK
		}
	}
}
