package com.avitoohband.nutrun

enum class ActiveWorkoutLayoutMode {
    LIST,
    GRID;

    companion object {
        fun fromStoredValue(raw: String?): ActiveWorkoutLayoutMode = when (raw) {
            GRID.name -> GRID
            LIST.name -> LIST
            else -> LIST
        }
    }
}
