require('dotenv').config();
const express = require('express');
const cors = require('cors');
const admin = require('firebase-admin');
const { RtcTokenBuilder, RtcRole } = require('agora-access-token');
const cloudinary = require('cloudinary').v2;
const fs = require('fs');

const requiredEnvVars = [
  'AGORA_APP_ID',
  'AGORA_APP_CERTIFICATE',
  'CLOUDINARY_CLOUD_NAME',
  'CLOUDINARY_API_KEY',
  'CLOUDINARY_API_SECRET'
];

requiredEnvVars.forEach(key => {
  if (!process.env[key]) {
    console.error(`❌ Missing env variable: ${key}`);
    process.exit(1);
  }
});

// Initialize Firebase Admin
try {
  if (fs.existsSync('./serviceAccountKey.json')) {
    const serviceAccount = require('./serviceAccountKey.json');
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount)
    });
  } else {
    admin.initializeApp();
  }
} catch (error) {
  console.log("Firebase Admin initialization skipped or failed:", error.message);
}

// Configure Cloudinary
cloudinary.config({
  cloud_name: process.env.CLOUDINARY_CLOUD_NAME,
  api_key: process.env.CLOUDINARY_API_KEY,
  api_secret: process.env.CLOUDINARY_API_SECRET,
  secure: true
});

const app = express();
app.use(cors());
app.use(express.json());

// Middleware to authenticate Firebase Users
const authenticateFirebaseUser = async (req, res, next) => {
  const authHeader = req.headers.authorization;
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'Unauthorized: Missing or invalid token' });
  }

  const idToken = authHeader.split('Bearer ')[1];
  try {
    const decodedToken = await admin.auth().verifyIdToken(idToken);
    req.user = decodedToken; // Attach user info to request
    next();
  } catch (error) {
    console.error("Firebase verifyIdToken error:", error);
    return res.status(403).json({ error: 'Unauthorized: Invalid token' });
  }
};

// --- AGORA TOKEN API ---
// Example usage: POST /api/agora/token
app.post('/api/agora/token', authenticateFirebaseUser, (req, res) => {
  const { channelName, uid } = req.body;

  if (!channelName) {
    return res.status(400).json({ error: 'channelName is required' });
  }

  const appId = process.env.AGORA_APP_ID;
  const appCertificate = process.env.AGORA_APP_CERTIFICATE;

  if (!appId || !appCertificate || appCertificate === 'your_agora_app_certificate_here') {
    return res.status(500).json({ error: 'Agora App ID and Certificate are not configured on the server.' });
  }

  // Set token expiration (e.g., 1 hour)
  const expirationTimeInSeconds = 3600;
  const currentTimestamp = Math.floor(Date.now() / 1000);
  const privilegeExpiredTs = currentTimestamp + expirationTimeInSeconds;

  // Use uid from query or default to 0 (which lets Agora assign a UID)
  const intUid = uid ? parseInt(uid, 10) : 0;

  try {
    const token = RtcTokenBuilder.buildTokenWithUid(
      appId,
      appCertificate,
      channelName,
      intUid,
      RtcRole.PUBLISHER,
      privilegeExpiredTs
    );
    res.json({ token, appId });
  } catch (err) {
    console.error("Error generating Agora token:", err);
    res.status(500).json({ error: 'Failed to generate token' });
  }
});

// --- CLOUDINARY SIGNATURE API ---
// Example usage: POST /api/cloudinary/signature
app.post('/api/cloudinary/signature', authenticateFirebaseUser, (req, res) => {
  const timestamp = Math.round((new Date).getTime() / 1000);

  const clientTimestamp = req.body.timestamp;
  if (clientTimestamp && Math.abs(timestamp - clientTimestamp) > 300) {
    return res.status(400).json({ error: 'Request expired' });
  }

  // You can pass any other parameters you want to sign
  const paramsToSign = {
    timestamp: timestamp,
    // Add other params like folder, upload_preset if needed
  };

  try {
    const signature = cloudinary.utils.api_sign_request(
      paramsToSign,
      process.env.CLOUDINARY_API_SECRET
    );

    res.json({
      signature,
      timestamp,
      api_key: process.env.CLOUDINARY_API_KEY,
      cloud_name: process.env.CLOUDINARY_CLOUD_NAME
    });
  } catch (error) {
    console.error("Error generating Cloudinary signature:", error);
    res.status(500).json({ error: 'Failed to generate signature' });
  }
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`Backend API is running on http://localhost:${PORT}`);
});
