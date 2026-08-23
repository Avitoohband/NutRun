import test from "node:test";
import assert from "node:assert/strict";
import { assertToolContract, MCP_CONTRACT_VERSION } from "../src/mcpContract.js";
import { toolDefinitions, MCP_CONTRACT_VERSION as toolsVersion } from "../src/tools.js";

test("MCP contract version is published consistently", () => {
  assert.equal(toolsVersion, MCP_CONTRACT_VERSION);
  assert.match(MCP_CONTRACT_VERSION, /^\d{4}-\d{2}-\d{2}-v\d+$/);
});

test("tool catalog passes contract validation", () => {
  const names = assertToolContract(toolDefinitions);
  assert.ok(names.includes("get_training_summary"));
  assert.ok(names.includes("get_training_program"));
  assert.ok(names.includes("log_weight"));
  assert.ok(names.includes("list_weight_entries"));
  assert.ok(names.includes("get_hydration_plan"));
  assert.ok(names.includes("get_reminder_settings"));
  assert.ok(names.includes("update_reminder_settings"));
  assert.equal(names.length, toolDefinitions.length);
});

test("log_workout schema accepts set-level detail", () => {
  const workoutTool = toolDefinitions.find((tool) => tool.name === "log_workout");
  assert.ok(workoutTool);
  assert.ok(workoutTool.inputSchema.properties.sets);
  assert.deepEqual(workoutTool.inputSchema.required, ["idempotencyKey", "name", "completedAt"]);
});
