import * as userService from './user-service';
import * as csvImportService from './csv-import-service';
import * as aiService from './ai-service';
import * as groupService from './group-service';
import * as courseService from './course-service';
import * as profileService from './profile-service';
import * as quizService from './quiz-service';

Object.assign(window, {
  __userService: userService,
  __csvImportService: csvImportService,
  __aiService: aiService,
  __groupService: groupService,
  __courseService: courseService,
  __profileService: profileService,
  __quizService: quizService,
});
