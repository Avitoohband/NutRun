export const MCP_CONTRACT_VERSION = "2026-08-24-v1";

export function assertToolContract(tools) {
  const names = tools.map((tool) => tool.name);
  const duplicates = names.filter((name, index) => names.indexOf(name) !== index);
  if (duplicates.length > 0) {
    throw new Error(`Duplicate MCP tool names: ${duplicates.join(", ")}`);
  }
  if (!names.includes("get_profile_summary")) {
    throw new Error("Missing baseline MCP tool: get_profile_summary");
  }
  return names;
}
