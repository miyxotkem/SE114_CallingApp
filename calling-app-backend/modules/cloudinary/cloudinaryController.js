const cloudinary = require('../../config/cloudinary');

const generateCloudinarySignature = (req, res) => {
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
};

module.exports = {
  generateCloudinarySignature
};
