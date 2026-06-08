const express = require('express');
const router = express.Router();
const { generateAgoraToken } = require('./agoraController');
const { authenticateFirebaseUser } = require('../../middlewares/auth');

// POST /api/v1/agora/token
router.post('/token', authenticateFirebaseUser, generateAgoraToken);

module.exports = router;
