import * as userService from './user-service';
import * as csvImportService from './csv-import-service';
import * as aiService from './ai-service';

Object.assign(window, { __userService: userService, __csvImportService: csvImportService, __aiService: aiService });
