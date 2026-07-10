import * as userService from './user-service';
import * as csvImportService from './csv-import-service';
import * as aiService from './ai-service';
import * as certificateService from './certificate-service';

Object.assign(window, { __userService: userService, __csvImportService: csvImportService, __aiService: aiService, __certificateService: certificateService });
import * as groupService from './group-service';
import * as courseService from './course-service';

Object.assign(window, { __userService: userService, __csvImportService: csvImportService, __groupService: groupService, __courseService: courseService, __certificateService: certificateService });
import * as profileService from './profile-service';
import * as dashboardService from './dashboard-service';

Object.assign(window, {
  __userService: userService,
  __csvImportService: csvImportService,
  __aiService: aiService,
  __groupService: groupService,
  __courseService: courseService,
  __profileService: profileService,
  __dashboardService: dashboardService,
  __certificateService: certificateService,
});
