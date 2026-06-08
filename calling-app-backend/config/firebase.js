const admin = require('firebase-admin');
const fs = require('fs');
const path = require('path');

try {
  // Use path.resolve to find key relative to root
  const serviceAccountPath = path.resolve(__dirname, '../serviceAccountKey.json');
  if (fs.existsSync(serviceAccountPath)) {
    const serviceAccount = require(serviceAccountPath);
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount)
    });
  } else {
    admin.initializeApp();
  }
} catch (error) {
  console.log("Firebase Admin initialization skipped or failed:", error.message);
}

module.exports = admin;
