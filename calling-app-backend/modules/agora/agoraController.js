const { RtcTokenBuilder, RtcRole } = require('agora-access-token');

const generateAgoraToken = (req, res) => {
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
};

module.exports = {
  generateAgoraToken
};
