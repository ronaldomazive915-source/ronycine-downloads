const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

const db = admin.firestore();

/**
 * Firestore Trigger: When a new notification event is created in `notificationEvents/{eventId}`,
 * query active devices with valid FCM tokens and permissions, and dispatch push notifications via FCM.
 */
exports.onNotificationEventCreated = functions.firestore
    .document("notificationEvents/{eventId}")
    .onCreate(async (snap, context) => {
        const eventId = context.params.eventId;
        const data = snap.data();

        console.log(`[FCM Push] Processing notification event: ${eventId}`, data);

        if (!data || data.status === "SENT") {
            console.log(`[FCM Push] Event already processed or empty: ${eventId}`);
            return null;
        }

        const title = data.title || "RONYCINE";
        const message = data.message || "Confira as novidades!";
        const imageUrl = data.imageUrl || null;
        const type = data.type || "GENERAL";
        const actionUrl = data.actionUrl || "";

        try {
            // Update status to SENDING
            await snap.ref.set({ status: "SENDING", updatedAt: admin.firestore.FieldValue.serverTimestamp() }, { merge: true });

            // Fetch eligible devices
            // Criteria: accessStatus == ACTIVE, notificationsEnabled == true, notificationsPermission == CONCEDIDA, valid fcmToken
            const devicesSnapshot = await db.collection("devices")
                .where("accessStatus", "==", "ACTIVE")
                .where("notificationsEnabled", "==", true)
                .where("notificationsPermission", "==", "CONCEDIDA")
                .get();

            const tokens = [];
            const deviceIdsByToken = new Map();

            devicesSnapshot.forEach(doc => {
                const dev = doc.data();
                const token = dev.fcmToken;
                if (token && typeof token === "string" && token.trim().length > 10) {
                    tokens.push(token);
                    deviceIdsByToken.set(token, doc.id);
                }
            });

            console.log(`[FCM Push] Found ${tokens.length} eligible device tokens for notification event ${eventId}`);

            if (tokens.length === 0) {
                await snap.ref.set({
                    status: "SENT",
                    sentCount: 0,
                    failedCount: 0,
                    completedAt: admin.firestore.FieldValue.serverTimestamp(),
                    note: "No eligible devices found"
                }, { merge: true });
                return null;
            }

            let sentCount = 0;
            let failedCount = 0;
            let invalidTokensCount = 0;

            // Batch tokens in chunks of 500 (FCM limit per multicast batch)
            const batchSize = 500;
            for (let i = 0; i < tokens.length; i += batchSize) {
                const batchTokens = tokens.slice(i, i + batchSize);

                const messagePayload = {
                    tokens: batchTokens,
                    notification: {
                        title: title,
                        body: message,
                        ...(imageUrl ? { imageUrl: imageUrl } : {})
                    },
                    data: {
                        type: type,
                        title: title,
                        message: message,
                        actionUrl: actionUrl,
                        eventId: eventId
                    },
                    android: {
                        priority: "high",
                        notification: {
                            channelId: "ronycine_news",
                            sound: "default"
                        }
                    }
                };

                try {
                    const response = await admin.messaging().sendEachForMulticast(messagePayload);
                    sentCount += response.successCount;
                    failedCount += response.failureCount;

                    response.responses.forEach((resp, idx) => {
                        if (!resp.success) {
                            const failedToken = batchTokens[idx];
                            const errorCode = resp.error ? resp.error.code : "";
                            console.error(`[FCM Push] Failed to send to token ${failedToken}:`, errorCode);

                            if (
                                errorCode === "messaging/invalid-registration-token" ||
                                errorCode === "messaging/registration-token-not-registered" ||
                                errorCode === "InvalidArgument"
                            ) {
                                invalidTokensCount++;
                                const devId = deviceIdsByToken.get(failedToken);
                                if (devId) {
                                    db.collection("devices").doc(devId).set({
                                        fcmToken: "",
                                        fcmStatus: "INVALIDO",
                                        notificationsEnabled: false,
                                        lastFcmError: errorCode,
                                        updatedAt: admin.firestore.FieldValue.serverTimestamp()
                                    }, { merge: true }).catch(err => console.error("Error clearing invalid token:", err));
                                }
                            }
                        }
                    });
                } catch (batchErr) {
                    console.error(`[FCM Push] Batch send error:`, batchErr);
                    failedCount += batchTokens.length;
                }
            }

            const finalStatus = failedCount === 0 ? "SENT" : (sentCount > 0 ? "PARTIAL" : "FAILED");

            await snap.ref.set({
                status: finalStatus,
                sentCount: sentCount,
                failedCount: failedCount,
                invalidTokensCount: invalidTokensCount,
                totalTargeted: tokens.length,
                completedAt: admin.firestore.FieldValue.serverTimestamp()
            }, { merge: true });

            console.log(`[FCM Push] Notification event ${eventId} finished. Status: ${finalStatus}, Sent: ${sentCount}, Failed: ${failedCount}, Invalid: ${invalidTokensCount}`);

        } catch (error) {
            console.error(`[FCM Push] Error processing notification event ${eventId}:`, error);
            await snap.ref.set({
                status: "FAILED",
                error: error.message,
                completedAt: admin.firestore.FieldValue.serverTimestamp()
            }, { merge: true });
        }

        return null;
    });
