# Quick Start Guide - Adaptive AI Study Planner

## Prerequisites

- Java 17+
- Maven 3.8+
- Node.js 16+
- Oracle Database (or compatible)
- Gemini API Key

## Setup Instructions

### 1. Backend Setup

```bash
# Navigate to backend directory
cd backend

# Install dependencies and compile
mvn clean compile

# Start the application
mvn spring-boot:run
```

Expected output:
```
Tomcat started on port 9090 (http) with context path ''
Started AasaBackendApplication in X.XXX seconds
```

### 2. Frontend Setup

```bash
# Navigate to frontend directory
cd frontend

# Install dependencies
npm install

# Start development server
npm run dev
```

Expected output:
```
  VITE v4.X.X  ready in XXX ms

  ➜  Local:   http://localhost:5173/
```

### 3. Access the Application

Open browser and navigate to: `http://localhost:5173`

---

## First Time Usage

### Step 1: Register Account
1. Click "Register" on login page
2. Enter email and password
3. Click "Sign Up"

### Step 2: Upload PDF
1. Click "Upload PDF" button
2. Select a PDF file from your computer
3. Enter exam date
4. Click "Upload & Analyze"
5. Wait for analysis to complete (1-2 minutes)

### Step 3: Take Quiz
1. Navigate to "Study" page
2. Select a topic
3. Answer quiz questions
4. Submit answers
5. View feedback

### Step 4: Check Dashboard
1. Navigate to "Dashboard"
2. View your progress
3. See weak topics
4. Check priority ranking

---

## Configuration

### Backend Configuration

Edit `backend/src/main/resources/application.properties`:

```properties
# Database Connection
spring.datasource.url=jdbc:oracle:thin:@//localhost:1521/xepdb1
spring.datasource.username=aasa_user
spring.datasource.password=aasa_password

# Gemini API Key (REQUIRED)
gemini.api.key=your_actual_gemini_api_key_here

# Server Port
server.port=9090
```

### Frontend Configuration

Edit `frontend/src/api.js`:

```javascript
const API_BASE_URL = 'http://localhost:9090/api'
```

---

## Troubleshooting

### Backend Won't Start

**Error: Port 9090 already in use**
```bash
# Kill the process using port 9090
lsof -ti:9090 | xargs kill -9

# Or use a different port
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=9091"
```

**Error: Database connection failed**
- Verify Oracle database is running
- Check credentials in application.properties
- Ensure database user has proper permissions

**Error: Gemini API key not configured**
- Set `gemini.api.key` in application.properties
- Use actual API key, not placeholder

### Frontend Won't Start

**Error: Port 5173 already in use**
```bash
npm run dev -- --port 5174
```

**Error: Module not found**
```bash
# Clear node_modules and reinstall
rm -rf node_modules package-lock.json
npm install
```

### PDF Upload Fails

**Error: File type not supported**
- Ensure file is PDF format
- Check file extension is .pdf

**Error: File too large**
- Maximum file size is 50MB
- Compress or split large PDFs

### Quiz Generation Takes Too Long

- Gemini API has 60-second timeout
- Very large PDFs may take longer
- Check internet connection

---

## System Architecture

```
Frontend (React)
    ↓
API Gateway (Spring Boot)
    ↓
Services Layer
    ├── PdfExtractionService
    ├── GeminiAiService
    ├── TopicAnalysisService
    ├── QuizEngineService
    ├── StudyProgressService
    └── DashboardService
    ↓
Repository Layer (JPA)
    ↓
Database (Oracle)
```

---

## Key Features

### 1. PDF Analysis
- Automatic text extraction
- Text cleaning and normalization
- AI-powered topic identification
- Semantic signal analysis

### 2. Quiz Generation
- AI-generated questions from PDF content
- Multiple difficulty levels
- Answer validation
- Explanation feedback

### 3. Adaptive Learning
- Dynamic weakness tracking
- Priority-based topic ranking
- Real-time dashboard updates
- Personalized recommendations

### 4. Progress Tracking
- Quiz attempt history
- Performance metrics
- Completion percentage
- Best scores

---

## API Endpoints

### Authentication
```
POST /api/auth/register    - Register new user
POST /api/auth/login       - Login user
```

### PDF Management
```
POST   /api/pdfs/upload    - Upload PDF
GET    /api/pdfs           - Get user's PDFs
GET    /api/pdfs/{id}      - Get specific PDF
DELETE /api/pdfs/{id}      - Delete PDF
```

### Topics
```
POST /api/topics/analyze/{pdfId}  - Analyze PDF
GET  /api/topics/pdf/{pdfId}      - Get topics
GET  /api/topics/ranked           - Get ranked topics
GET  /api/topics/{id}             - Get topic details
```

### Quizzes
```
GET  /api/quizzes/topic/{id}      - Get quizzes
GET  /api/quizzes/{id}            - Get quiz
POST /api/quizzes/{id}/submit     - Submit answer
```

### Dashboard
```
GET /api/dashboard                - Get dashboard data
```

---

## Monitoring

### Check Backend Logs
```bash
# View logs in real-time
tail -f backend/target/logs/*.log

# Search for errors
grep "ERROR" backend/target/logs/*.log

# Search for specific operations
grep "PDF extraction" backend/target/logs/*.log
```

### Check Database
```sql
-- Count PDFs
SELECT COUNT(*) FROM pdf_documents;

-- Count topics
SELECT COUNT(*) FROM topics;

-- Count quizzes
SELECT COUNT(*) FROM quizzes;

-- Check user progress
SELECT * FROM study_progress WHERE user_id = 1;
```

---

## Performance Tips

1. **Optimize PDF Size**
   - Use compressed PDFs
   - Limit to 10-20 pages for faster analysis

2. **Batch Quiz Taking**
   - Answer multiple quizzes in one session
   - Reduces database round trips

3. **Clear Browser Cache**
   - Clears old dashboard data
   - Ensures fresh data display

4. **Monitor API Calls**
   - Check Gemini API usage
   - Avoid excessive re-analysis

---

## Security Best Practices

1. **Change Default Credentials**
   - Update database password
   - Update JWT secret

2. **Use HTTPS in Production**
   - Configure SSL certificates
   - Update CORS settings

3. **Protect API Keys**
   - Never commit API keys to git
   - Use environment variables

4. **Regular Backups**
   - Backup database regularly
   - Backup uploaded PDFs

---

## Support

For issues or questions:

1. Check logs for error messages
2. Review troubleshooting section
3. Check API endpoint documentation
4. Review database schema

---

## Next Steps

1. Upload your first PDF
2. Review generated topics
3. Take some quizzes
4. Check dashboard for adaptive updates
5. Explore recommendations

Enjoy your adaptive learning experience!
