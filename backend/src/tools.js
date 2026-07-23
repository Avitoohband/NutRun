export const toolDefinitions = [
  {
    name: "get_profile_summary",
    description: "Read health estimates and current targets.",
    inputSchema: { type: "object", properties: {}, additionalProperties: false }
  },
  {
    name: "get_daily_summary",
    description: "Read calories, macros, water, supplements, and training for a local date.",
    inputSchema: {
      type: "object",
      properties: { date: { type: "string", format: "date" } },
      required: ["date"],
      additionalProperties: false
    }
  },
  {
    name: "search_food",
    description: "Search the configured Open Food Facts adapter.",
    inputSchema: {
      type: "object",
      properties: { query: { type: "string", minLength: 2, maxLength: 120 } },
      required: ["query"],
      additionalProperties: false
    }
  },
  {
    name: "log_food",
    description: "Create a meal-based food log.",
    inputSchema: {
      type: "object",
      properties: {
        idempotencyKey: { type: "string" },
        date: { type: "string", format: "date" },
        mealType: { enum: ["BREAKFAST", "LUNCH", "DINNER", "SNACK"] },
        name: { type: "string" },
        servingGrams: { type: "number", exclusiveMinimum: 0 },
        calories: { type: "integer", minimum: 0 },
        proteinGrams: { type: "number", minimum: 0 },
        carbohydrateGrams: { type: "number", minimum: 0 },
        fatGrams: { type: "number", minimum: 0 }
      },
      required: ["idempotencyKey", "date", "mealType", "name", "calories"],
      additionalProperties: false
    }
  },
  {
    name: "log_water",
    description: "Create a water log.",
    inputSchema: {
      type: "object",
      properties: {
        idempotencyKey: { type: "string" },
        date: { type: "string", format: "date" },
        amountMl: { type: "integer", minimum: 1, maximum: 5000 }
      },
      required: ["idempotencyKey", "date", "amountMl"],
      additionalProperties: false
    }
  },
  {
    name: "log_supplement_status",
    description: "Record a supplement status.",
    inputSchema: {
      type: "object",
      properties: {
        idempotencyKey: { type: "string" },
        supplementId: { type: "string" },
        date: { type: "string", format: "date" },
        completed: { type: "boolean" }
      },
      required: ["idempotencyKey", "supplementId", "date", "completed"],
      additionalProperties: false
    }
  },
  {
    name: "log_workout",
    description: "Create a completed workout summary.",
    inputSchema: {
      type: "object",
      properties: {
        idempotencyKey: { type: "string" },
        name: { type: "string" },
        completedAt: { type: "string", format: "date-time" }
      },
      required: ["idempotencyKey", "name", "completedAt"],
      additionalProperties: true
    }
  },
  {
    name: "list_walks",
    description: "List walk summaries without coordinates.",
    inputSchema: { type: "object", properties: {}, additionalProperties: false }
  },
  {
    name: "get_walk_summary",
    description: "Read one walk summary without coordinates.",
    inputSchema: {
      type: "object",
      properties: { walkId: { type: "string" } },
      required: ["walkId"],
      additionalProperties: false
    }
  },
  {
    name: "get_walk_route",
    description: "Read full route geometry. Requires location.read.",
    inputSchema: {
      type: "object",
      properties: { walkId: { type: "string" } },
      required: ["walkId"],
      additionalProperties: false
    }
  },
  {
    name: "update_profile",
    description: "Update profile fields after explicit confirmation.",
    inputSchema: {
      type: "object",
      properties: { confirmed: { type: "boolean" }, patch: { type: "object" } },
      required: ["confirmed", "patch"],
      additionalProperties: false
    }
  },
  {
    name: "delete_log",
    description: "Delete a log after explicit confirmation.",
    inputSchema: {
      type: "object",
      properties: {
        confirmed: { type: "boolean" },
        collection: { enum: ["foodLogs", "waterLogs", "supplementLogs", "workouts"] },
        logId: { type: "string" }
      },
      required: ["confirmed", "collection", "logId"],
      additionalProperties: false
    }
  }
];
