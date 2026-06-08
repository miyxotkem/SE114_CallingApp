const admin = require('../config/firebase');

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

module.exports = {
  authenticateFirebaseUser
};
