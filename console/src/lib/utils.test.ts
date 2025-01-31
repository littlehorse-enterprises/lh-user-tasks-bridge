import { test } from "node:test";
import assert from "node:assert";
import { getRoles } from "./utils";

test("getRoles with roles and permissions", () => {
  process.env.LHUT_AUTHORITIES =
    "$.realm_access.roles,$.resource_access.*.roles";

  const testObj = {
    realm_access: {
      roles: ["admin", "editor"],
    },
    resource_access: {
      admin: {
        roles: ["read", "write"],
      },
    },
  };

  const expectedRoles = ["admin", "editor", "read", "write"];
  const result = getRoles(testObj);
  assert.deepStrictEqual(
    result,
    expectedRoles,
    "Should return all roles and permissions",
  );
});

test("getRoles with one matching path (no wildcard)", () => {
  process.env.LHUT_AUTHORITIES =
    "$.realm_access.roles,$.resource_access.*.roles";

  const testObj = {
    realm_access: {
      roles: ["admin", "editor"],
    },
  };

  const expectedRoles = ["admin", "editor"];
  const result = getRoles(testObj);
  assert.deepStrictEqual(
    result,
    expectedRoles,
    "Should return roles from the matching path",
  );
});

test("getRoles with one matching path (with wildcard)", () => {
  process.env.LHUT_AUTHORITIES =
    "$.realm_access.roles,$.resource_access.*.roles";

  const testObj = {
    resource_access: {
      admin: {
        roles: ["read", "write"],
      },
    },
  };

  const expectedRoles = ["read", "write"];
  const result = getRoles(testObj);
  assert.deepStrictEqual(
    result,
    expectedRoles,
    "Should return roles from the matching path",
  );
});

test("getRoles with no matching paths", () => {
  process.env.LHUT_AUTHORITIES =
    "$.realm_access.roles,$.resource_access.*.roles";

  const obj = {
    guest: {
      access: ["view"],
    },
  };

  const expectedRoles: any[] = [];
  const result = getRoles(obj);
  assert.deepStrictEqual(
    result,
    expectedRoles,
    "Should return an empty array for no matching paths",
  );
});
