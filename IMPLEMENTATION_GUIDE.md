# Adaptive AI Study Planner - Complete System Implementation Guide

## Executive Summary

The Adaptive AI Study Planner has been fully debugged, fixed, and connected. All broken systems have been restored to working order with comprehensive error handling, logging, and validation.

### What Was Fixed

1. **PDF Extraction** - Complete text cleaning pipeline
2. **Gemini API Integration** - Robust error handling and response parsing
3. **Quiz Generation** - Comprehensive validation and error handling
4. **Priority Algorithm** - Dynamic recalculation after each quiz attempt
5. **Weakness Tracking** - Proper score-based weakness level calculation
6. **Dashboard Updates** - Real-time adaptive updates based on performance
7. **Backend Services** - All services properly connected with dependency injection
8. **End-to-End Workflow** - Complete pipeline from PDF upload to adaptive recommendations

---

## System Architecture

### Backend Stack
- **Framework**: Spring Boot 3.2.0
- **Database**: Oracle Database
- **ORM**: Hibernate 6.3.1
- **PDF Processing**: Apache PDFBox 2.0.29
- **AI Integration**: Google Gemini API
- **Authentication**: JWT with Spring Security

### Frontend Stack
- **Framework**: React.js
- **HTTP Client**: Axios
- **UI Components**: Lucide Icons
- **Charts**: Recharts

---

## Complete Workflow

### Phase 1: PDF Upload & Extraction

**Endpoint**: `POST /api/pdfs/upload`

```
User Action: Upload PDF + Exam Date
    ↓
PdfController.uploadPdf()
    ↓
Validation:
  - File type check (PDF only)
  - File size check
  - User authentication
    ↓
PdfManagementService.uploadPdf()
    ↓
PdfExtractionService.extractTextFromPdf()
    ↓
Text Cleaning:
  - Remove empty lines
  - Normalize whitespace
  - Remove corrupted characters
  - UTF-8 encoding
    ↓
PdfDocument saved to database
    ↓
Response: PdfDocumentDto with ID
```

**Key Classes**:
- `PdfController` - HTTP endpoint
- `PdfManagementService` - Business logic
- `PdfExtractionService` - PDF processing
- `PdfDocument` - Entity

---

### Phase 2: Content Analysis with AI

**Endpoint**: `POST /api/topics/analyze/{pdfId}`

```
User Action: Click "Analyze" button
    ↓
TopicController.analyzePdf()
    ↓
Retrieve PdfDocument
    ↓
TopicAnalysisService.analyzeAndCreateTopics()
    ↓
GeminiAiService.analyzeContent()
    ↓
Build Prompt:
  - Include extracted text
  - Specify JSON format
  - Add constraints (no outside knowledge)
    ↓
Call Gemini API:
  - 60-second timeout
  - Error handling
  - Response parsing
    ↓
Parse Response:
  - Extract JSON array
  - Validate structure
  - Convert to TopicAnalysis objects
    ↓
For Each Topic:
  - Calculate complexity score
  - Calculate importance score
  - Calculate priority score
  - Save Topic entity
    ↓
Topics saved to database
```

**Scoring Formulas**:

```
Complexity = (0.4 * conceptDensity/10) + 
             (0.3 * keywordDifficulty/10) + 
             (0.2 * formulaScore) + 
             (0.1 * normalizedLength)

Importance = (0.6 * conceptWeight) + 
             (0.4 * difficultyWeight)

Priority = Complexity * Importance
```

**Key Classes**:
- `TopicController` - HTTP endpoint
- `TopicAnalysisService` - Analysis orchestration
- `GeminiAiService` - AI integration
- `Topic` - Entity

---

### Phase 3: Quiz Generation

**Automatic after Phase 2**

```
For Each Topic:
    ↓
TopicController.analyzePdf() continues
    ↓
QuizEngineService.generateQuizzesForTopic()
    ↓
For Each AI-Generated Question:
  - Validate question text
  - Validate 4 options exist
  - Validate answer in options
  - Check for duplicates
  - Ensure no empty fields
    ↓
If Valid:
  - Create Quiz entity
  - Save to database
    ↓
If Invalid:
  - Log warning
  - Skip question
    ↓
Mark PDF as analyzed
    ↓
Response: List of TopicDtos with quiz counts
```

**Validation Rules**:
- Question text must not be empty
- Exactly 4 options required
- Correct answer must be in options
- No duplicate options
- No null or empty values

**Key Classes**:
- `QuizEngineService` - Quiz generation
- `Quiz` - Entity
- `QuizRepository` - Data access

---

### Phase 4: Quiz Attempt & Scoring

**Endpoint**: `POST /api/quizzes/{quizId}/submit`

```
User Action: Answer quiz question
    ↓
QuizController.submitQuiz()
    ↓
Retrieve Quiz
    ↓
Compare Answer:
  - Trim whitespace
  - Case-insensitive comparison
    ↓
Create QuizAttempt:
  - Save selected answer
  - Mark correct/incorrect
  - Record time taken
    ↓
StudyProgressService.updateProgressAfterQuizAttempt()
    ↓
Get or Create StudyProgress
    ↓
Update Metrics:
  - Increment total attempts
  - Increment correct attempts if right
  - Calculate percentage score
  - Update best score
    ↓
Calculate Weakness Level:
  - Score >= 75 = LOW
  - Score 50-74 = MEDIUM
  - Score < 50 = HIGH
    ↓
Update StudyProgress
    ↓
Trigger Priority Recalculation
    ↓
Response: QuizSubmissionResponse with feedback
```

**Key Classes**:
- `QuizController` - HTTP endpoint
- `StudyProgressService` - Progress tracking
- `WeaknessEngineService` - Weakness calculation
- `QuizAttempt` - Entity
- `StudyProgress` - Entity

---

### Phase 5: Dynamic Priority Recalculation

**Triggered after each quiz attempt**

```
StudyProgressService.updateTopicPriorities()
    ↓
For Each Topic of User:
    ↓
Retrieve StudyProgress
    ↓
Calculate Metrics:
  - Days until exam
  - Weakness score (0.2 to 1.0)
  - Complexity score (0.0 to 1.0)
  - Importance score (0.0 to 1.0)
    ↓
Calculate Urgency:
  urgency = 1 / (daysUntilExam + 1)
    ↓
Calculate Priority:
  Priority = (0.35 * complexity) +
             (0.25 * importance) +
             (0.25 * weakness) +
             (0.15 * urgency)
    ↓
Update Topic priority score
    ↓
Save to database
    ↓
Topics automatically reranked
```

**Weakness Score Mapping**:
- LOW (>= 75%) = 0.2
- MEDIUM (50-74%) = 0.5
- HIGH (< 50%) = 0.9
- NOT_ATTEMPTED = 1.0

**Key Classes**:
- `StudyProgressService` - Priority recalculation
- `ScoringEngineService` - Score calculations
- `WeaknessEngineService` - Weakness mapping

---

### Phase 6: Dashboard & Adaptive Updates

**Endpoint**: `GET /api/dashboard`

```
User Action: View Dashboard
    ↓
DashboardController.getDashboard()
    ↓
DashboardService.generateDashboard()
    ↓
Fetch User Data:
  - All PDFs
  - All Topics
  - All Quizzes
  - All Quiz Attempts
    ↓
Calculate Metrics:
  - Total PDFs
  - Total Topics
  - Total Quizzes
  - Average Score
  - Days until exam
    ↓
Get Ranked Topics:
  - Ordered by priority score (DESC)
  - With progress metrics
    ↓
Get Weak Topics:
  - Filter by HIGH/MEDIUM weakness
  - Ordered by priority
    ↓
Calculate Overall Completion:
  - Average of all topic completion %
    ↓
Build DashboardDto
    ↓
Response: Complete dashboard data
```

**Dashboard Displays**:
- Total PDFs uploaded
- Total Topics identified
- Average quiz score
- Days until exam
- Top 5 priority topics with progress
- Weak topics needing focus
- Overall completion percentage

**Key Classes**:
- `DashboardController` - HTTP endpoint
- `DashboardService` - Dashboard generation
- `DashboardDto` - Response DTO

---

## API Endpoints

### PDF Management
```
POST   /api/pdfs/upload              - Upload PDF with exam date
GET    /api/pdfs                     - Get user's PDFs
GET    /api/pdfs/{pdfId}             - Get specific PDF
DELETE /api/pdfs/{pdfId}             - Delete PDF
```

### Topic Analysis
```
POST   /api/topics/analyze/{pdfId}   - Analyze PDF and generate quizzes
GET    /api/topics/pdf/{pdfId}       - Get topics for PDF
GET    /api/topics/ranked            - Get ranked topics for user
GET    /api/topics/{topicId}         - Get specific topic
```

### Quiz Management
```
GET    /api/quizzes/topic/{topicId}  - Get quizzes for topic
GET    /api/quizzes/{quizId}         - Get specific quiz
POST   /api/quizzes/{quizId}/submit  - Submit quiz answer
```

### Dashboard & Analytics
```
GET    /api/dashboard                - Get dashboard data
GET    /api/analytics/performance    - Get performance metrics
GET    /api/analytics/topic/{id}     - Get topic analytics
GET    /api/recommendations/next-topics - Get recommended topics
```

---

## Database Schema

### Tables
```
users
├── id (PK)
├── email (UNIQUE)
├── password
├── name
└── created_at

pdf_documents
├── id (PK)
├── user_id (FK)
├── file_name
├── file_path
├── upload_date
├── exam_date
├── extracted_text (CLOB)
├── is_analyzed
└── topics (1:N)

topics
├── id (PK)
├── pdf_id (FK)
├── title
├── description
├── concept_density
├── keyword_difficulty
├── formula_count
├── content_length
├── complexity_score
├── importance_score
├── priority_score
├── quizzes (1:N)
└── study_progress (1:N)

quizzes
├── id (PK)
├── topic_id (FK)
├── question (CLOB)
├── option_a
├── option_b
├── option_c
├── option_d
├── correct_answer
├── difficulty
├── explanation (CLOB)
└── attempts (1:N)

quiz_attempts
├── id (PK)
├── user_id (FK)
├── quiz_id (FK)
├── selected_answer
├── is_correct
├── marks_obtained
├── attempt_time
└── time_taken_seconds

study_progress
├── id (PK)
├── user_id (FK)
├── topic_id (FK)
├── weakness_level
├── completion_percentage
├── best_score
├── total_attempts
└── correct_attempts
```

### Sequences
```
user_id_seq
pdf_id_seq
topic_id_seq
quiz_id_seq
study_progress_id_seq
quiz_attempt_id_seq
```

---

## Configuration

### application.properties
```properties
# Server
server.port=9090

# Database
spring.datasource.url=jdbc:oracle:thin:@//localhost:1521/xepdb1
spring.datasource.username=aasa_user
spring.datasource.password=aasa_password
spring.datasource.driver-class-name=oracle.jdbc.OracleDriver

# JPA/Hibernate
spring.jpa.database-platform=org.hibernate.dialect.OracleDialect
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false

# File Upload
file.upload.dir=uploads/pdfs
spring.servlet.multipart.max-file-size=50MB
spring.servlet.multipart.max-request-size=50MB

# JWT
jwt.secret=your_super_secret_jwt_key_change_in_production_environment_12345678901234567890
jwt.expiration=86400000

# Gemini API
gemini.api.key=your_actual_gemini_api_key_here
gemini.model.name=gemini-1.5-flash

# CORS
cors.allowed-origins=http://localhost:3000,http://localhost:3001,http://localhost:5173
cors.allowed-methods=GET,POST,PUT,DELETE,OPTIONS
cors.allowed-headers=Content-Type,Authorization,X-Requested-With,Accept
cors.allow-credentials=true

# Logging
logging.level.root=INFO
logging.level.com.aasa=DEBUG
logging.level.org.springframework.security=DEBUG
```

---

## Testing the System

### 1. Start Backend
```bash
cd backend
mvn spring-boot:run
```

Expected output:
```
Tomcat started on port 9090 (http) with context path ''
Started AasaBackendApplication in X.XXX seconds
```

### 2. Start Frontend
```bash
cd frontend
npm install
npm run dev
```

### 3. Test PDF Upload
1. Navigate to `http://localhost:5173/upload`
2. Select a PDF file
3. Enter exam date
4. Click "Upload & Analyze"
5. Monitor backend logs for:
   - PDF extraction
   - Text cleaning
   - Gemini API call
   - Topic creation
   - Quiz generation

### 4. Test Quiz Taking
1. Navigate to `http://localhost:5173/study`
2. Select a topic
3. Answer quiz questions
4. Submit answers
5. Check dashboard for:
   - Updated weakness levels
   - Reranked topics
   - Updated completion %

### 5. Verify Adaptive Updates
1. Answer multiple quizzes
2. Check dashboard after each submission
3. Verify:
   - Weakness levels change
   - Topic priorities recalculate
   - Weak topics appear in recommendations

---

## Logging & Debugging

### Log Levels
- **INFO**: Major operations (upload, analysis, quiz submission)
- **DEBUG**: Detailed operations (scoring, priority calculation)
- **WARNING**: Non-critical issues (invalid questions filtered)
- **SEVERE**: Errors (API failures, validation errors)

### Key Log Points
```
PDF Extraction:
  "Starting PDF extraction from file: {filename}"
  "Raw text extracted, length: {length}"
  "Text cleaned, final length: {length}"

Gemini API:
  "Starting content analysis with Gemini API"
  "Text length: {length}"
  "Calling Gemini API: {url}"
  "Received response with status code: {code}"

Topic Analysis:
  "Analyzing PDF document: {filename}"
  "Received {count} topics from AI"
  "Created {count} topic entities"

Quiz Generation:
  "Generating quizzes for topic: {title}"
  "Validated {count} out of {total} questions"

Priority Recalculation:
  "Recalculating priorities for all topics of user {id}"
  "Topic: {title} - Weakness: {score}, Priority: {score}"
```

---

## Error Handling

### Common Errors & Solutions

**1. Port 9090 Already in Use**
```bash
# Kill the process
lsof -ti:9090 | xargs kill -9
# Or use different port
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=9091"
```

**2. Gemini API Key Not Configured**
```
Error: Gemini API key is not configured
Solution: Set gemini.api.key in application.properties
```

**3. PDF Extraction Fails**
```
Error: Failed to extract text from PDF
Solution: Ensure PDF is valid and not corrupted
```

**4. Quiz Generation Skips Questions**
```
Warning: One of the options is empty
Solution: Gemini response format issue - check prompt
```

**5. Database Connection Fails**
```
Error: Unable to acquire JDBC Connection
Solution: Verify Oracle database is running and credentials are correct
```

---

## Performance Optimization

### Caching Strategies
- Topics are cached in memory after analysis
- Quiz questions are retrieved from database on demand
- Dashboard data is calculated on request (no caching)

### Database Optimization
- Indexes on frequently queried columns:
  - `user_id` in all user-related tables
  - `topic_id` in quizzes and study_progress
  - `pdf_id` in topics

### API Optimization
- Gemini API calls are limited to 60 seconds
- Text is truncated to 10,000 characters for API calls
- Batch operations for saving multiple entities

---

## Security Considerations

### Authentication
- JWT tokens required for all protected endpoints
- Token expiration: 24 hours
- Tokens stored in localStorage on frontend

### Authorization
- Users can only access their own PDFs and progress
- Quiz attempts are tied to authenticated user
- Dashboard shows only user's data

### Data Protection
- Passwords hashed with Spring Security
- CORS enabled for frontend domains only
- SQL injection prevention via JPA

---

## Future Enhancements

1. **Real-time Updates**
   - WebSocket for live dashboard updates
   - Push notifications for weak topics

2. **Advanced Analytics**
   - Learning curve analysis
   - Predictive performance scoring
   - Study time optimization

3. **Collaborative Features**
   - Study groups
   - Peer quiz sharing
   - Discussion forums

4. **Mobile App**
   - React Native implementation
   - Offline quiz mode
   - Mobile-optimized UI

5. **Advanced AI**
   - Custom prompt engineering
   - Multi-language support
   - Adaptive difficulty adjustment

---

## Support & Troubleshooting

### Common Issues

**Dashboard not updating after quiz**
- Check that StudyProgressService.updateTopicPriorities() is called
- Verify database transactions are committed
- Check browser cache

**Quizzes not generated**
- Verify Gemini API key is valid
- Check PDF extraction succeeded
- Review backend logs for API errors

**PDF upload fails**
- Verify file is valid PDF
- Check file size < 50MB
- Ensure write permissions for upload directory

### Debug Mode
```bash
mvn spring-boot:run -Ddebug
```

This enables detailed logging and allows remote debugging on port 5005.

---

## Conclusion

The Adaptive AI Study Planner is now fully functional with:
- ✅ Complete PDF extraction and cleaning
- ✅ Robust AI integration with Gemini
- ✅ Comprehensive quiz generation
- ✅ Dynamic weakness tracking
- ✅ Adaptive priority recalculation
- ✅ Real-time dashboard updates
- ✅ Full end-to-end workflow
- ✅ Production-ready code quality
- ✅ Comprehensive logging and error handling

The system is ready for deployment and user testing.
