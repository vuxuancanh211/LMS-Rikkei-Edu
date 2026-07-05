import * as userService from './user-service';
import * as csvImportService from './csv-import-service';
import * as aiService from './ai-service';

Object.assign(window, { __userService: userService, __csvImportService: csvImportService, __aiService: aiService });
import * as groupService from './group-service';
import * as courseService from './course-service';

Object.assign(window, { __userService: userService, __csvImportService: csvImportService, __groupService: groupService, __courseService: courseService });
import * as profileService from './profile-service';

Object.assign(window, { __userService: userService, __csvImportService: csvImportService, __profileService: profileService });
