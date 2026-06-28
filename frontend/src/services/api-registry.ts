import * as userService from './user-service';
import * as csvImportService from './csv-import-service';
import * as quizService from './quiz-service';

Object.assign(window, {
  __userService: userService,
  __csvImportService: csvImportService,
  __quizService: quizService,
});
