const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const ADMIN_UID = "admin_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function ts(offsetMs = -2000) {
  return new Date(Date.now() + offsetMs);
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
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  if (testEnv) await testEnv.clearFirestore();
});

test("Unauthenticated users and blocked monetization collections are denied", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("videos").where("isRemoved", "==", false).where("isPublic", "==", true).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("wallet").doc(ALICE_UID).set({ balance: 100 }));
  await assertFails(aliceDb.collection("payouts").doc("p1").set({ amount: 50 }));
});

test("User profile, PII isolation, and notification preferences enforce owner isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = ts();

  // Alice creates own public profile (cannot self-assign ADMIN)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      username: "alice_tech",
      displayName: "Alice Tech",
      role: "ADMIN",
      isApprovedUploader: true,
      isSuspended: false,
      subscriberCount: 0,
      isPrivateProfile: false,
      hideSubscriberCount: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      username: "alice_tech",
      displayName: "Alice Tech",
      bio: "Creator tips",
      channelDescription: "Welcome",
      avatarUri: "drawable://img_thumb_tech",
      bannerUri: "drawable://img_banner_studio",
      role: "CREATOR",
      isApprovedUploader: true,
      isSuspended: false,
      subscriberCount: 0,
      isPrivateProfile: false,
      hideSubscriberCount: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Alice stores PII in private subcollection; Bob cannot read it
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("private").doc("info").set({
      uid: ALICE_UID,
      emailMasked: "al***@example.com",
      updatedAt: now,
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("private").doc("info").get());

  // Alice updates own notification preferences; Bob is denied
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("notificationPreferences").doc("settings").set({
      userId: ALICE_UID,
      likesEnabled: false,
      commentsEnabled: true,
      repliesEnabled: true,
      subscribersEnabled: true,
      videoUploadsEnabled: true,
      updatedAt: now,
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("notificationPreferences").doc("settings").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("notificationPreferences").doc("settings").update({ likesEnabled: true })
  );
});

test("Notifications enforce recipient-only read/mark-read/delete and actor integrity", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = ts();

  // Bob sends a LIKE notification to Alice
  await assertSucceeds(
    bobDb.collection("users").doc(ALICE_UID).collection("notifications").doc(`like_vid_1_${BOB_UID}`).set({
      recipientUserId: ALICE_UID,
      actorId: BOB_UID,
      actorName: "Bob",
      actorAvatarUri: "drawable://img_creator_paisa_logo",
      type: "LIKE",
      videoId: "vid_1",
      videoThumbnailUri: "drawable://img_thumb_tech",
      commentId: null,
      message: "liked your video \"Desk Tour\"",
      isRead: false,
      dedupKey: "like:vid_1:bob_456",
      createdAt: now,
    })
  );

  // Bob checks his own deduped notification existence (allowed) vs Charlie probing Bob's (denied)
  await assertSucceeds(
    bobDb.collection("users").doc(ALICE_UID).collection("notifications").doc(`like_vid_1_${BOB_UID}`).get()
  );
  const charlieDb = testEnv.authenticatedContext("charlie_999").firestore();
  await assertFails(
    charlieDb.collection("users").doc(ALICE_UID).collection("notifications").doc(`like_vid_1_${BOB_UID}`).get()
  );

  // Self-notification rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("notifications").doc("like_self").set({
      recipientUserId: ALICE_UID,
      actorId: ALICE_UID,
      actorName: "Alice",
      type: "LIKE",
      message: "self like",
      isRead: false,
      dedupKey: "like:vid_1:alice_123",
      createdAt: now,
    })
  );

  // Bob cannot list/read/mark-read/delete Alice's notifications
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("notifications").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("notifications").doc(`like_vid_1_${BOB_UID}`).update({ isRead: true }));
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("notifications").doc(`like_vid_1_${BOB_UID}`).delete());

  // Alice lists, marks as read (only isRead allowed), and deletes
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("notifications").get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("notifications").doc(`like_vid_1_${BOB_UID}`).update({ isRead: true })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("notifications").doc(`like_vid_1_${BOB_UID}`).update({ message: "Tampered" })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("notifications").doc(`like_vid_1_${BOB_UID}`).delete()
  );
});

test("Videos enforce duration caps, atomic like counters, likedVideos, and filtered queries", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const now = ts(-10000);
    await db.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      username: "alice_tech",
      displayName: "Alice Tech",
      role: "CREATOR",
      isApprovedUploader: true,
      isSuspended: false,
      subscriberCount: 0,
      isPrivateProfile: false,
      hideSubscriberCount: false,
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = ts();

  // Short > 180s fails
  await assertFails(
    aliceDb.collection("videos").doc("short_bad").set({
      creatorId: ALICE_UID,
      creatorName: "Alice Tech",
      title: "Too Long Short",
      format: "SHORT",
      category: "TECH",
      durationSeconds: 200,
      fileSizeBytes: 1024,
      videoUri: "https://example.com/s.mp4",
      viewCount: 0,
      likeCount: 0,
      commentCount: 0,
      reportCount: 0,
      isPublic: true,
      isRemoved: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Invalid tags (>10 items or non-string element) fails
  await assertFails(
    aliceDb.collection("videos").doc("vid_bad_tags").set({
      creatorId: ALICE_UID,
      creatorName: "Alice Tech",
      title: "Bad Tags",
      format: "LONG",
      category: "TECH",
      tags: ["a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k"],
      durationSeconds: 300,
      fileSizeBytes: 500000,
      videoUri: "https://example.com/v1.mp4",
      viewCount: 0,
      likeCount: 0,
      commentCount: 0,
      reportCount: 0,
      isPublic: true,
      isRemoved: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Valid video with tags succeeds
  await assertSucceeds(
    aliceDb.collection("videos").doc("vid_1").set({
      creatorId: ALICE_UID,
      creatorName: "Alice Tech",
      title: "Desk Tour",
      description: "Setup guide",
      format: "LONG",
      category: "TECH",
      tags: ["tech", "lighting", "studio"],
      durationSeconds: 300,
      fileSizeBytes: 500000,
      thumbnailUri: "drawable://img_thumb_tech",
      videoUri: "https://example.com/v1.mp4",
      viewCount: 0,
      likeCount: 0,
      commentCount: 0,
      reportCount: 0,
      isPublic: true,
      isRemoved: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Filtered query matches rule (including tag array-contains); unfiltered query fails
  await assertSucceeds(bobDb.collection("videos").where("isRemoved", "==", false).where("isPublic", "==", true).get());
  await assertSucceeds(
    bobDb.collection("videos")
      .where("isRemoved", "==", false)
      .where("isPublic", "==", true)
      .where("tags", "array-contains", "tech")
      .get()
  );
  await assertFails(bobDb.collection("videos").get());

  // Creator can update tags metadata; non-creator cannot
  await assertSucceeds(
    aliceDb.collection("videos").doc("vid_1").update({
      tags: ["tech", "lighting", "desk"],
      updatedAt: ts(-1500),
    })
  );
  await assertFails(
    bobDb.collection("videos").doc("vid_1").update({
      tags: ["hacked"],
      updatedAt: ts(-1400),
    })
  );

  // Atomic batch: Bob likes vid_1, writes users/bob/likedVideos/vid_1, and increments likeCount by 1
  const batch = bobDb.batch();
  batch.set(bobDb.collection("videos").doc("vid_1").collection("likes").doc(BOB_UID), {
    userId: BOB_UID,
    createdAt: now,
  });
  batch.set(bobDb.collection("users").doc(BOB_UID).collection("likedVideos").doc("vid_1"), {
    videoId: "vid_1",
    createdAt: now,
  });
  batch.update(bobDb.collection("videos").doc("vid_1"), {
    likeCount: 1,
    updatedAt: now,
  });
  await assertSucceeds(batch.commit());

  // Alice cannot read Bob's likedVideos subcollection
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("likedVideos").get());

  // Future timestamp tampered write fails
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).collection("watchHistory").doc("vid_1").set({
      videoId: "vid_1",
      positionMs: 5000,
      watchedAt: new Date(Date.now() + 60000),
    })
  );

  // Fake likeCount jump without like doc fails
  await assertFails(
    bobDb.collection("videos").doc("vid_1").update({ likeCount: 999, updatedAt: ts(-500) })
  );
});
