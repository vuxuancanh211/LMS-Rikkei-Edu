import * as userService from './user-service';
import * as csvImportService from './csv-import-service';
import * as profileService from './profile-service';

Object.assign(window, { __userService: userService, __csvImportService: csvImportService, __profileService: profileService });
