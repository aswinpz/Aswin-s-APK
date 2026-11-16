const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-51ea7207-4b16-44c1-919e-29905b71fedc";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validPayload(uid, overrides = {}) {
  const pastTime = new Date(Date.now() - 2000);
  return {
    userId: uid,
    sku: "NK-AM90-BLK-10",
    binNumber: "A-12",
    modelName: "Nike Air Max 90 Black",
    barcodes: ["0194957382912"],
    quantity: 4,
    notes: "Top shelf",
    createdAt: pastTime,
    updatedAt: pastTime,
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or list shoe_items", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore(DATABASE_ID);
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("shoe_items").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("shoe_items")
      .doc("item_1")
      .get()
  );
});

test("Authenticated user can create, list, update, and delete own shoe_items", async () => {
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID)
    .firestore(DATABASE_ID);
  const docRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("shoe_items")
    .doc("item_1");

  await assertSucceeds(docRef.set(validPayload(ALICE_UID)));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("shoe_items").get()
  );
  await assertSucceeds(
    docRef.update({
      binNumber: "B-04",
      quantity: 6,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(docRef.delete());
});

test("Cross-user isolation: Bob cannot read, create, update, or delete Alice's shoe_items", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore(DATABASE_ID)
      .collection("users")
      .doc(ALICE_UID)
      .collection("shoe_items")
      .doc("alice_item")
      .set(validPayload(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  const aliceCollection = bobDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("shoe_items");

  await assertFails(aliceCollection.get());
  await assertFails(aliceCollection.doc("alice_item").get());
  await assertFails(
    aliceCollection.doc("bob_inject").set(validPayload(BOB_UID))
  );
  await assertFails(
    aliceCollection
      .doc("alice_item")
      .update({ binNumber: "Z-99", updatedAt: new Date(Date.now() - 1000) })
  );
  await assertFails(aliceCollection.doc("alice_item").delete());
});

test("Dirty Dozen: rejects identity spoofing, ghost fields, immutable createdAt change, and invalid types/sizes", async () => {
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID)
    .firestore(DATABASE_ID);
  const col = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("shoe_items");

  // Identity spoofing
  await assertFails(col.doc("spoof").set(validPayload(BOB_UID)));

  // Ghost field (Shadow Update test)
  await assertFails(
    col.doc("ghost").set(validPayload(ALICE_UID, { isAdmin: true }))
  );

  // Empty SKU
  await assertFails(
    col.doc("empty_sku").set(validPayload(ALICE_UID, { sku: "" }))
  );

  // Negative quantity
  await assertFails(
    col.doc("neg_qty").set(validPayload(ALICE_UID, { quantity: -1 }))
  );

  // Invalid document ID characters
  await assertFails(col.doc("bad.id!").set(validPayload(ALICE_UID)));

  // Immutable createdAt mutation on update
  const validDoc = col.doc("item_valid");
  await assertSucceeds(validDoc.set(validPayload(ALICE_UID)));
  await assertFails(
    validDoc.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
});
