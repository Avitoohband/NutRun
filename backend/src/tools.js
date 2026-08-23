import { MCP_CONTRACT_VERSION } from "./mcpContract.js";

export { MCP_CONTRACT_VERSION };

const workoutSetSchema = {
  type: "object",
  properties: {
    exerciseId: { type: "string" },
    exerciseName: { type: "string" },
    setNumber: { type: "integer", minimum: 1 },
    reps: { type: "integer", minimum: 0 },
    weightKg: { type: "number", minimum: 0 },
    durationSeconds: { type: "integer", minimum: 0 },
    rpe: { type: "number", minimum: 0, maximum: 10 },
    completed: { type: "boolean" }
  },
  additionalProperties: false
};

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
    description: "Create a completed workout with optional per-set detail.",
    inputSchema: {
      type: "object",
      properties: {
        idempotencyKey: { type: "string" },
        name: { type: "string" },
        completedAt: { type: "string", format: "date-time" },
        date: { type: "string", format: "date" },
        sessionId: { type: "string" },
        sets: {
          type: "array",
          items: workoutSetSchema
        }
      },
      required: ["idempotencyKey", "name", "completedAt"],
      additionalProperties: false
    }
  },
  {
    name: "get_training_summary",
    description: "Read active workout state and recent workout counts from synced training data.",
    inputSchema: { type: "object", properties: {}, additionalProperties: false }
  },
  {
    name: "get_training_program",
    description: "Read weekly schedule and reusable workout templates from synced training data.",
    inputSchema: { type: "object", properties: {}, additionalProperties: false }
  },
  {
    name: "list_weight_entries",
    description: "List recent body-weight entries in canonical kilograms.",
    inputSchema: {
      type: "object",
      properties: { limit: { type: "integer", minimum: 1, maximum: 100 } },
      additionalProperties: false
    }
  },
  {
    name: "log_weight",
    description: "Record a body-weight entry in canonical kilograms.",
    inputSchema: {
      type: "object",
      properties: {
        idempotencyKey: { type: "string" },
        weightKg: { type: "number", exclusiveMinimum: 0, maximum: 500 },
        recordedAt: { type: "string", format: "date-time" }
      },
      required: ["idempotencyKey", "weightKg"],
      additionalProperties: false
    }
  },
  {
    name: "get_hydration_plan",
    description: "Read the synced hydration goal, serving size, and reminder window.",
    inputSchema: { type: "object", properties: {}, additionalProperties: false }
  },
  {
    name: "get_reminder_settings",
    description:
      "Read reminder settings stored for MCP clients. Mobile reminder toggles may remain device-local until cloud sync is enabled.",
    inputSchema: { type: "object", properties: {}, additionalProperties: false }
  },
  {
    name: "update_reminder_settings",
    description:
      "Update MCP-visible reminder settings after explicit confirmation. Does not yet replace device-local mobile reminder storage.",
    inputSchema: {
      type: "object",
      properties: {
        confirmed: { type: "boolean" },
        patch: {
          type: "object",
          properties: {
            water: { type: "object" },
            training: { type: "object" },
            supplement: { type: "object" }
          },
          additionalProperties: false
        }
      },
      required: ["confirmed", "patch"],
      additionalProperties: false
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
