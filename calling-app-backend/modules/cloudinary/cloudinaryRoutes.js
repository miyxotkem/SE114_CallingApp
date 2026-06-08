const express = require('express');
const router = express.Router();
const { generateCloudinarySignature } = require('./cloudinaryController');
const { authenticateFirebaseUser } = require('../../middlewares/auth');

// POST /api/v1/cloudinary/signature
router.post('/signature', authenticateFirebaseUser, generateCloudinarySignature);

module.exports = router;
