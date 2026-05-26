# AASA API Documentation

## Base URL
```
http://localhost:8080/api
```

## Authentication
All endpoints except `/auth/*` require JWT token in Authorization header:
```
Authorization: Bearer {token}
```

---

## Authentication Endpoints

### Register User
Create a new user account.

**Request**
```
POST /auth/register
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "securePassword123",
  "name": "John Doe"
}
```

**Response** (201 Created)
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "email": "user@example.com",
  "name": "John Doe",
  "userId": 1
}
```

**Error Responses**
- `400 Bad Request` - Invalid input or email already exists
- `500 Internal Server Error` - Server error

---

### Login User
Authenticate and receive JWT token.

**Request**
```
POST /auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "securePassword123"
}
```

**Response** (200 OK)
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "email": "user@example.com",
  "name": "John Doe",
  "userId": 1
}
```

**Error Responses**
- `401 Unauthorized` - Invalid credentials
- `500 Internal Server Error` - Server error

---

## PDF Management Endpoints

### Upload PDF
Upload a PDF file for analysis.

**Request**
```
POST /pdfs/upload
Authorization: Bearer {token}
Content-Type: multipart/form-data

file: <binary_pdf_file>
examDate: 2026-06-15
```

**Response** (201 Created)
```json
{
  "id": 1,
  "fileName": "Biology_Chapter_5.pdf",
  "uploadDate": "2026-05-23T10:30:00",
  "examDate": "2026-06-15",
  "isAnalyzed": false,
  "topicCount": 0
}
```

**Error Responses**
- `400 Bad Request` - Invalid file or missing exam date
- `401 Unauthorized` - Invalid token
- `413 Payload Too Large` - File exceeds 50MB limit
- `500 Internal Server Error` - Upload failed

---

### Get User PDFs
Retrieve all PDFs uploaded by the authenticated user.

**Request**
```
GET /pdfs
Authorization: Bearer {token}
```

**Response** (200 OK)
```json
[
  {
    "id": 1,
    "fileName": "Biology_Chapter_5.pdf",
    "uploadDate": "2026-05-23T10:30:00",
    "examDate": "2026-06-15",
    "isAnalyzed": true,
    "topicCount": 5
  },
  {
    "id": 2,
    "fileName": "Chemistry_Basics.pdf",
    "uploadDate": "2026-05-22T14:15:00",
    "examDate": "2026-06-20",
    "isAnalyzed": false,
    "topicCount": 0
  }
]
```

**Error Responses**
- `401 Unauthorized` - Invalid token
- `500 Internal Server Error` - Server error

---

### Get PDF by ID
Retrieve a specific PDF document.

**Request**
```
GET /pdfs/{pdfId}
Authorization: Bearer {token}
```

**Response** (200 OK)
```json
{
  "id": 1,
  "fileName": "Biology_Chapter_5.pdf",
  "uploadDate": "2026-05-23T10:30:00",
  "examDate": "2026-06-15",
  "isAnalyzed": true,
  "topicCount": 5
}
```

**Error Responses**
- `401 Unauthorized` - Invalid token
- `404 Not Found` - PDF not found
- `500 Internal Server Error` - Server error

---

### Delete PDF
Delete a PDF and all associated data.

**Request**
```
DELETE /pdfs/{pdfId}
Authorization: Bearer {token}
```

**Response** (204 No Content)
```
(empty body)
```

**Error Responses**
- `401 Unauthorized` - Invalid token
- `404 Not Found` - PDF not found
- `500 Internal Server Error` - Deletion failed

---

## Topic Analysis Endpoints

### Analyze PDF
Analyze a PDF using Gemini AI to extract topics and generate quizzes.

**Request**
```
POST /topics/analyze/{pdfId}
Authorization: Bearer {token}
```

**Response** (201 Created)
```json
[
  {
    "id": 1,
    "title": "Genetics and Heredity",
    "description": "Study of genes and inheritance patterns",
    "complexityScore": 0.75,
    "importanceScore": 0.85,
    "priorityScore": 0.80,
    "quizCount": 3
  },
  {
    "id": 2,
    "title": "DNA Structure",
    "description": "Understanding DNA molecules",
    "complexityScore": 0.65,
    "importanceScore": 0.90,
    "priorityScore": 0.78,
    "quizCount": 2
  }
]
```

**Error Responses**
- `400 Bad Request` - PDF not found or invalid
- `401 Unauthorized` - Invalid token
- `500 Internal Server Error` - Analysis failed

---

### Get Topics by PDF
Retrieve all topics extracted from a specific PDF.

**Request**
```
GET /topics/pdf/{pdfId}
Authorization: Bearer {token}
```

**Response** (200 OK)
```json
[
  {
    "id": 1,
    "title": "Genetics and Heredity",
    "description": "Study of genes and inheritance patterns",
    "complexityScore": 0.75,
    "importanceScore": 0.85,
    "priorityScore": 0.80,
    "quizCount": 3
  }
]
```

**Error Responses**
- `401 Unauthorized` - Invalid token
- `404 Not Found` - PDF not found
- `500 Internal Server Error` - Server error

---

### Get Ranked Topics
Retrieve all user's topics ranked by priority score.

**Request**
```
GET /topics/ranked
Authorization: Bearer {token}
```

**Response** (200 OK)
```json
[
  {
    "id": 2,
    "title": "DNA Structure",
    "description": "Understanding DNA molecules",
    "complexityScore": 0.65,
    "importanceScore": 0.90,
    "priorityScore": 0.78,
    "quizCount": 2
  },
  {
    "id": 1,
    "title": "Genetics and Heredity",
    "description": "Study of genes and inheritance patterns",
    "complexityScore": 0.75,
    "importanceScore": 0.85,
    "priorityScore": 0.80,
    "quizCount": 3
  }
]
```

**Error Responses**
- `401 Unauthorized` - Invalid token
- `500 Internal Server Error` - Server error

---

### Get Topic by ID
Retrieve a specific topic.

**Request**
```
GET /topics/{topicId}
Authorization: Bearer {token}
```

**Response** (200 OK)
```json
{
  "id": 1,
  "title": "Genetics and Heredity",
  "description": "Study of genes and inheritance patterns",
  "complexityScore": 0.75,
  "importanceScore": 0.85,
  "priorityScore": 0.80,
  "quizCount": 3
}
```

**Error Responses**
- `401 Unauthorized` - Invalid token
- `404 Not Found` - Topic not found
- `500 Internal Server Error` - Server error

---

## Quiz Endpoints

### Get Quizzes by Topic
Retrieve all quizzes for a specific topic.

**Request**
```
GET /quizzes/topic/{topicId}
Authorization: Bearer {token}
```

**Response** (200 OK)
```json
[
  {
    "id": 1,
    "topicId": 1,
    "question": "What are the building blocks of DNA?",
    "optionA": "Proteins",
    "optionB": "Nucleotides",
    "optionC": "Lipids",
    "optionD": "Carbohydrates",
    "difficulty": "EASY"
  },
  {
    "id": 2,
    "topicId": 1,
    "question": "Which base pairs with Adenine?",
    "optionA": "Guanine",
    "optionB": "Cytosine",
    "optionC": "Thymine",
    "optionD": "Uracil",
    "difficulty": "MEDIUM"
  }
]
```

**Error Responses**
- `401 Unauthorized` - Invalid token
- `404 Not Found` - Topic not found
- `500 Internal Server Error` - Server error

---

### Get Quiz by ID
Retrieve a specific quiz question.

**Request**
```
GET /quizzes/{quizId}
Authorization: Bearer {token}
```

**Response** (200 OK)
```json
{
  "id": 1,
  "topicId": 1,
  "question": "What are the building blocks of DNA?",
  "optionA": "Proteins",
  "optionB": "Nucleotides",
  "optionC": "Lipids",
  "optionD": "Carbohydrates",
  "difficulty": "EASY"
}
```

**Error Responses**
- `401 Unauthorized` - Invalid token
- `404 Not Found` - Quiz not found
- `500 Internal Server Error` - Server error

---

### Submit Quiz Answer
Submit an answer to a quiz question and receive feedback.

**Request**
```
POST /quizzes/{quizId}/submit
Authorization: Bearer {token}
Content-Type: application/json

{
  "selectedAnswer": "Nucleotides",
  "timeTakenSeconds": 45
}
```

**Response** (200 OK)
```json
{
  "isCorrect": true,
  "correctAnswer": "Nucleotides",
  "explanation": "Nucleotides are the basic structural units of DNA, consisting of a sugar (deoxyribose), a phosphate group, and a nitrogenous base.",
  "marksObtained": 1.0
}
```

**Error Response (Incorrect Answer)**
```json
{
  "isCorrect": false,
  "correctAnswer": "Nucleotides",
  "explanation": "Nucleotides are the basic structural units of DNA, consisting of a sugar (deoxyribose), a phosphate group, and a nitrogenous base.",
  "marksObtained": 0.0
}
```

**Error Responses**
- `400 Bad Request` - Invalid answer or missing fields
- `401 Unauthorized` - Invalid token
- `404 Not Found` - Quiz not found
- `500 Internal Server Error` - Submission failed

---

## Dashboard Endpoint

### Get Dashboard Data
Retrieve comprehensive dashboard analytics.

**Request**
```
GET /dashboard
Authorization: Bearer {token}
```

**Response** (200 OK)
```json
{
  "totalPdfs": 3,
  "totalTopics": 12,
  "totalQuizzes": 45,
  "averageScore": 78.5,
  "daysUntilExam": 24,
  "rankedTopics": [
    {
      "topicId": 2,
      "topicTitle": "DNA Structure",
      "weaknessLevel": "LOW",
      "completionPercentage": 85.0,
      "bestScore": 90.0,
      "totalAttempts": 10,
      "correctAttempts": 9,
      "priorityScore": 0.78
    },
    {
      "topicId": 1,
      "topicTitle": "Genetics and Heredity",
      "weaknessLevel": "MEDIUM",
      "completionPercentage": 65.0,
      "bestScore": 70.0,
      "totalAttempts": 8,
      "correctAttempts": 5,
      "priorityScore": 0.80
    }
  ],
  "weakTopics": [
    {
      "topicId": 3,
      "topicTitle": "Protein Synthesis",
      "weaknessLevel": "HIGH",
      "completionPercentage": 40.0,
      "bestScore": 45.0,
      "totalAttempts": 5,
      "correctAttempts": 2,
      "priorityScore": 0.85
    }
  ],
  "overallCompletionPercentage": 63.3
}
```

**Error Responses**
- `401 Unauthorized` - Invalid token
- `500 Internal Server Error` - Server error

---

## Error Response Format

All error responses follow this format:

```json
{
  "timestamp": "2026-05-23T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid input provided",
  "path": "/api/endpoint"
}
```

---

## Rate Limiting

Currently, no rate limiting is implemented. In production, consider implementing:
- 100 requests per minute per user
- 1000 requests per hour per IP

---

## Pagination (Future)

Endpoints will support pagination:
```
GET /pdfs?page=0&size=10&sort=uploadDate,desc
```

---

## Versioning

Current API version: **v1**

Future versions will be available at `/api/v2/`, etc.

---

## Status Codes

- `200 OK` - Successful GET request
- `201 Created` - Successful POST request
- `204 No Content` - Successful DELETE request
- `400 Bad Request` - Invalid input
- `401 Unauthorized` - Missing or invalid token
- `404 Not Found` - Resource not found
- `413 Payload Too Large` - File too large
- `500 Internal Server Error` - Server error

---

## Example Workflow

1. **Register/Login**
   ```
   POST /auth/register
   ```

2. **Upload PDF**
   ```
   POST /pdfs/upload
   ```

3. **Analyze PDF**
   ```
   POST /topics/analyze/{pdfId}
   ```

4. **Get Topics**
   ```
   GET /topics/ranked
   ```

5. **Get Quizzes**
   ```
   GET /quizzes/topic/{topicId}
   ```

6. **Submit Answer**
   ```
   POST /quizzes/{quizId}/submit
   ```

7. **View Dashboard**
   ```
   GET /dashboard
   ```

---

**Last Updated**: 2026-05-23
**API Version**: 1.0.0
