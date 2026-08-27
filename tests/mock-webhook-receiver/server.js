// This Express application listens for incoming HTTP POST payloads and outputs formatted event traces
// to your local terminal, allowing you to inspect the JSON metadata fired by your custom event listener.

const express = require('express');
const app = express();
const PORT = process.env.PORT || 3000;

app.use(express.json());

// Print beautiful console telemetry for every incoming event
app.post('/webhook', (req, res) => {
    console.log(`\n=================== Webhook Caught [${new Date().toISOString()}] ===================`);
    console.log('Headers:', JSON.stringify(req.headers, null, 2));
    console.log('Payload:', JSON.stringify(req.body, null, 2));
    console.log('========================================================================\n');
    res.status(200).send({ status: 'ACCEPTED' });
});

app.listen(PORT, () => {
    console.log(`Mock Webhook Receiver initialized on port ${PORT}...`);
    console.log(`Local Destination Target URL: http://localhost:${PORT}/webhook`);
});
