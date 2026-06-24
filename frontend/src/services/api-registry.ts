import * as userService from './user-service';
import * as csvImportService from './csv-import-service';

Object.assign(window, { __userService: userService, __csvImportService: csvImportService });
