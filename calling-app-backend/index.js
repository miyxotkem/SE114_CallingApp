require('dotenv').config();
const express = require('express');
const cors = require('cors');

// Initialize Firebase configuration
require('./config/firebase');

// Import routes
const agoraRoutes = require('./modules/agora/agoraRoutes');
const cloudinaryRoutes = require('./modules/cloudinary/cloudinaryRoutes');

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

const app = express();

const corsOptions = {
  origin: process.env.ALLOWED_ORIGINS ? process.env.ALLOWED_ORIGINS.split(',') : '*',
  methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'Authorization']
};
app.use(cors(corsOptions));
app.use(express.json());

// Routes
// 1. Versioned APIs for the updated client structure
app.use('/api/v1/agora', agoraRoutes);
app.use('/api/v1/cloudinary', cloudinaryRoutes);

// 2. Backwards compatibility routes for older client versions
app.use('/api/agora', agoraRoutes);
app.use('/api/cloudinary', cloudinaryRoutes);

// Global Error Handler
app.use((err, req, res, next) => {
  console.error('❌ Unhandled Server Error:', err);
  res.status(500).json({ error: 'Internal Server Error' });
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`Backend API is running on http://localhost:${PORT}`);
});
