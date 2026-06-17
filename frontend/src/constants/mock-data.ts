/* ============================================================
   RIKKEI EDU — Mock data (Vietnamese LMS)
   ============================================================ */
(function () {
  const T = {
    react: "assets/courses/react.png",
    nodejs: "assets/courses/nodejs.png",
    java: "assets/courses/java.png",
    data1: "assets/courses/data1.png",
    data2: "assets/courses/data2.png",
    lesson: "assets/courses/lesson.png",
    card1: "assets/courses/card1.png",
  };

  // ---------- Avatar palette (initials) ----------
  const AV = ["#2563eb","#10b981","#f59e0b","#8b5cf6","#ec4899","#0ea5e9","#ef4444","#14b8a6","#6366f1","#f97316"];
  function avatarColor(name){ let h=0; for(const c of name) h=(h*31+c.charCodeAt(0))>>>0; return AV[h%AV.length]; }
  function initials(name){ const p=name.trim().split(/\s+/); return (p[0][0]+ (p.length>1? p[p.length-1][0]:"")).toUpperCase(); }

  // ---------- Courses (12) ----------
  const courses = [
    { id:"c1", title:"Lập trình ReactJS Nâng cao & Redux Toolkit", cat:"Frontend", instructor:"Nguyễn Văn An", thumb:T.react, students:842, lessons:48, hours:32, level:"Nâng cao", rating:4.8, progress:68, sStatus:"learning", pubStatus:"published", chapters:8 },
    { id:"c2", title:"Xây dựng RESTful API với NodeJS & Express", cat:"Backend", instructor:"Trần Thị Bình", thumb:T.nodejs, students:1204, lessons:52, hours:38, level:"Trung cấp", rating:4.7, progress:12, sStatus:"learning", pubStatus:"published", chapters:9 },
    { id:"c3", title:"Java Spring Boot Microservices", cat:"Backend", instructor:"Lê Văn Cường", thumb:T.java, students:967, lessons:60, hours:45, level:"Nâng cao", rating:4.9, progress:100, sStatus:"done", pubStatus:"published", chapters:10 },
    { id:"c4", title:"Cơ sở dữ liệu PostgreSQL Cơ bản", cat:"Database", instructor:"Phạm Thị Dung", thumb:T.data1, students:653, lessons:36, hours:24, level:"Cơ bản", rating:4.6, progress:0, sStatus:"new", pubStatus:"published", chapters:6 },
    { id:"c5", title:"Phát triển phần mềm với Agile / Scrum", cat:"Quy trình", instructor:"Đỗ Minh Quân", thumb:T.lesson, students:1089, lessons:40, hours:28, level:"Trung cấp", rating:4.8, progress:54, sStatus:"learning", pubStatus:"published", chapters:7 },
    { id:"c6", title:"Thiết kế Cơ sở dữ liệu & SQL Thực chiến", cat:"Database", instructor:"Phạm Thị Dung", thumb:T.data2, students:740, lessons:44, hours:30, level:"Trung cấp", rating:4.7, progress:100, sStatus:"done", pubStatus:"published", chapters:8 },
    { id:"c7", title:"DevOps với Docker & Kubernetes", cat:"DevOps", instructor:"Vũ Đức Hải", thumb:T.card1, students:521, lessons:50, hours:40, level:"Nâng cao", rating:4.9, progress:34, sStatus:"learning", pubStatus:"published", chapters:9 },
    { id:"c8", title:"TypeScript cho lập trình viên JavaScript", cat:"Frontend", instructor:"Nguyễn Văn An", thumb:T.react, students:430, lessons:30, hours:20, level:"Trung cấp", rating:4.6, progress:0, sStatus:"new", pubStatus:"published", chapters:5 },
    { id:"c9", title:"Kiểm thử tự động với Selenium & Jest", cat:"Testing", instructor:"Hoàng Thu Trang", thumb:T.lesson, students:312, lessons:28, hours:18, level:"Trung cấp", rating:4.5, progress:0, sStatus:"new", pubStatus:"pending", chapters:5 },
    { id:"c10", title:"Machine Learning Cơ bản với Python", cat:"AI/ML", instructor:"Lê Văn Cường", thumb:T.data1, students:0, lessons:54, hours:42, level:"Nâng cao", rating:0, progress:0, sStatus:"new", pubStatus:"pending", chapters:10 },
    { id:"c11", title:"Bảo mật ứng dụng Web (Web Security)", cat:"Security", instructor:"Vũ Đức Hải", thumb:T.nodejs, students:0, lessons:34, hours:26, level:"Nâng cao", rating:0, progress:0, sStatus:"new", pubStatus:"draft", chapters:6 },
    { id:"c12", title:"UI/UX Design cho lập trình viên", cat:"Design", instructor:"Hoàng Thu Trang", thumb:T.card1, students:688, lessons:32, hours:22, level:"Cơ bản", rating:4.8, progress:0, sStatus:"new", pubStatus:"published", chapters:6 },
  ];

  // ---------- Users (12) — Admin ----------
  const roles = ["Học viên","Giảng viên","Quản trị viên"];
  const users = [
    { id:"u1", name:"Nguyễn Văn An", email:"an.nguyen@rikkei.edu", role:"Giảng viên", status:"active", joined:"12/01/2024", courses:3, lastActive:"2 giờ trước" },
    { id:"u2", name:"Trần Thị Bình", email:"binh.tran@rikkei.edu", role:"Giảng viên", status:"active", joined:"05/02/2024", courses:2, lastActive:"1 ngày trước" },
    { id:"u3", name:"Lê Văn Cường", email:"cuong.le@rikkei.edu", role:"Giảng viên", status:"active", joined:"21/11/2023", courses:4, lastActive:"3 giờ trước" },
    { id:"u4", name:"Phạm Thị Dung", email:"dung.pham@rikkei.edu", role:"Giảng viên", status:"active", joined:"30/03/2024", courses:2, lastActive:"vừa xong" },
    { id:"u5", name:"Hoàng Văn Em", email:"em.hoang@gmail.com", role:"Học viên", status:"active", joined:"14/04/2024", courses:5, lastActive:"5 phút trước" },
    { id:"u6", name:"Vũ Thị Phương", email:"phuong.vu@gmail.com", role:"Học viên", status:"active", joined:"22/04/2024", courses:3, lastActive:"1 giờ trước" },
    { id:"u7", name:"Đỗ Minh Quân", email:"quan.do@rikkei.edu", role:"Giảng viên", status:"active", joined:"08/12/2023", courses:1, lastActive:"4 giờ trước" },
    { id:"u8", name:"Bùi Thị Hương", email:"huong.bui@gmail.com", role:"Học viên", status:"disabled", joined:"19/05/2024", courses:2, lastActive:"2 tuần trước" },
    { id:"u9", name:"Ngô Văn Khoa", email:"khoa.ngo@gmail.com", role:"Học viên", status:"active", joined:"01/06/2024", courses:4, lastActive:"30 phút trước" },
    { id:"u10", name:"Đặng Thu Hà", email:"ha.dang@gmail.com", role:"Học viên", status:"active", joined:"11/06/2024", courses:6, lastActive:"hôm qua" },
    { id:"u11", name:"Admin Rikkei", email:"admin@rikkei.edu", role:"Quản trị viên", status:"active", joined:"01/01/2023", courses:0, lastActive:"đang online" },
    { id:"u12", name:"Trịnh Văn Long", email:"long.trinh@gmail.com", role:"Học viên", status:"disabled", joined:"27/06/2024", courses:1, lastActive:"1 tháng trước" },
  ];

  // ---------- Pending approvals (8) — Admin ----------
  const approvals = [
    { id:"a1", course:"Machine Learning Cơ bản với Python", instructor:"Lê Văn Cường", cat:"AI/ML", date:"28/05/2026", lessons:54, status:"pending" },
    { id:"a2", course:"Kiểm thử tự động với Selenium & Jest", instructor:"Hoàng Thu Trang", cat:"Testing", date:"27/05/2026", lessons:28, status:"pending" },
    { id:"a3", course:"GraphQL API toàn tập", instructor:"Trần Thị Bình", cat:"Backend", date:"26/05/2026", lessons:36, status:"pending" },
    { id:"a4", course:"Flutter cho người mới bắt đầu", instructor:"Vũ Đức Hải", cat:"Mobile", date:"25/05/2026", lessons:42, status:"pending" },
    { id:"a5", course:"Next.js 14 & Server Components", instructor:"Nguyễn Văn An", cat:"Frontend", date:"24/05/2026", lessons:38, status:"approved" },
    { id:"a6", course:"Quản trị hệ thống Linux", instructor:"Đỗ Minh Quân", cat:"DevOps", date:"23/05/2026", lessons:30, status:"approved" },
    { id:"a7", course:"Thiết kế giao diện với Figma", instructor:"Hoàng Thu Trang", cat:"Design", date:"22/05/2026", lessons:26, status:"rejected" },
    { id:"a8", course:"Lập trình game với Unity", instructor:"Vũ Đức Hải", cat:"Game", date:"21/05/2026", lessons:48, status:"pending" },
  ];

  // ---------- Activity log (10) — Admin ----------
  const activity = [
    { who:"Admin Rikkei", act:'đã phê duyệt khóa học "Next.js 14 & Server Components"', time:"5 phút trước", type:"approve" },
    { who:"Nguyễn Văn An", act:'đã gửi khóa học mới chờ duyệt', time:"22 phút trước", type:"submit" },
    { who:"Hệ thống", act:'đã cấp 12 chứng chỉ cho khóa "Java Spring Boot"', time:"1 giờ trước", type:"cert" },
    { who:"Trần Thị Bình", act:'đã tạo nhóm mới "Nhóm B2 - NodeJS"', time:"2 giờ trước", type:"group" },
    { who:"Admin Rikkei", act:'đã vô hiệu hóa tài khoản "Trịnh Văn Long"', time:"3 giờ trước", type:"ban" },
    { who:"Phạm Thị Dung", act:'đã chấm điểm 24 bài tập', time:"4 giờ trước", type:"grade" },
    { who:"Hệ thống", act:'phát hiện vi phạm Proctoring của học viên "Ngô Văn Khoa"', time:"5 giờ trước", type:"warn" },
    { who:"Lê Văn Cường", act:'đã thêm 18 học viên vào khóa học', time:"hôm qua", type:"add" },
    { who:"Admin Rikkei", act:'đã từ chối khóa học "Thiết kế giao diện với Figma"', time:"hôm qua", type:"reject" },
    { who:"Đỗ Minh Quân", act:'đã xuất bản bài kiểm tra cuối khóa', time:"2 ngày trước", type:"publish" },
  ];

  // ---------- Assignments (10) — Student ----------
  const assignments = [
    { id:"as1", title:"Bài tập 1: Component & Props trong React", course:"Lập trình ReactJS Nâng cao", deadline:"02/06/2026", status:"graded", score:"9.5/10", type:"assignment" },
    { id:"as2", title:"Quiz chương 3: Hooks nâng cao", course:"Lập trình ReactJS Nâng cao", deadline:"04/06/2026", status:"pending", score:null, type:"quiz", proctored:true },
    { id:"as3", title:"Bài tập 2: Xây dựng REST API CRUD", course:"Xây dựng RESTful API với NodeJS", deadline:"06/06/2026", status:"submitted", score:null, type:"assignment" },
    { id:"as4", title:"Bài kiểm tra giữa kỳ Agile/Scrum", course:"Phát triển phần mềm với Agile", deadline:"01/06/2026", status:"late", score:null, type:"quiz", proctored:true },
    { id:"as5", title:"Bài tập 3: Truy vấn JOIN nâng cao", course:"Thiết kế CSDL & SQL Thực chiến", deadline:"08/06/2026", status:"pending", score:null, type:"assignment" },
    { id:"as6", title:"Quiz chương 2: Express Middleware", course:"Xây dựng RESTful API với NodeJS", deadline:"30/05/2026", status:"graded", score:"8.0/10", type:"quiz" },
    { id:"as7", title:"Bài tập 4: Dockerfile & Compose", course:"DevOps với Docker & Kubernetes", deadline:"10/06/2026", status:"pending", score:null, type:"assignment" },
    { id:"as8", title:"Bài kiểm tra cuối khóa Spring Boot", course:"Java Spring Boot Microservices", deadline:"28/05/2026", status:"graded", score:"9.0/10", type:"quiz", proctored:true },
    { id:"as9", title:"Quiz chương 1: Khái niệm DevOps", course:"DevOps với Docker & Kubernetes", deadline:"12/06/2026", status:"pending", score:null, type:"quiz" },
    { id:"as10", title:"Bài tập 5: Tối ưu truy vấn PostgreSQL", course:"Cơ sở dữ liệu PostgreSQL", deadline:"14/06/2026", status:"pending", score:null, type:"assignment" },
  ];

  // ---------- Groups (8) — Instructor ----------
  const groups = [
    { id:"g1", name:"Nhóm A1 - ReactJS K15", course:"Lập trình ReactJS Nâng cao", members:18, max:20, progress:72, start:"01/03/2026", end:"30/06/2026" },
    { id:"g2", name:"Nhóm A2 - ReactJS K15", course:"Lập trình ReactJS Nâng cao", members:20, max:20, progress:65, start:"01/03/2026", end:"30/06/2026" },
    { id:"g3", name:"Nhóm B1 - NodeJS K12", course:"Xây dựng RESTful API với NodeJS", members:15, max:18, progress:58, start:"15/03/2026", end:"15/07/2026" },
    { id:"g4", name:"Nhóm B2 - NodeJS K12", course:"Xây dựng RESTful API với NodeJS", members:16, max:18, progress:61, start:"15/03/2026", end:"15/07/2026" },
    { id:"g5", name:"Nhóm C1 - Spring Boot K9", course:"Java Spring Boot Microservices", members:22, max:24, progress:88, start:"10/02/2026", end:"10/06/2026" },
    { id:"g6", name:"Nhóm D1 - Agile K20", course:"Phát triển phần mềm với Agile", members:24, max:25, progress:54, start:"20/03/2026", end:"20/07/2026" },
    { id:"g7", name:"Nhóm E1 - DevOps K7", course:"DevOps với Docker & Kubernetes", members:12, max:15, progress:34, start:"01/04/2026", end:"01/08/2026" },
    { id:"g8", name:"Nhóm F1 - SQL K11", course:"Thiết kế CSDL & SQL Thực chiến", members:19, max:20, progress:76, start:"05/03/2026", end:"05/07/2026" },
  ];

  // ---------- Group students (12) ----------
  const groupStudents = [
    { name:"Hoàng Văn Em", joined:"01/03/2026", progress:88, submit:"Đã nộp", grade:"9.0" },
    { name:"Vũ Thị Phương", joined:"01/03/2026", progress:76, submit:"Đã nộp", grade:"8.5" },
    { name:"Ngô Văn Khoa", joined:"02/03/2026", progress:92, submit:"Đã nộp", grade:"9.5" },
    { name:"Đặng Thu Hà", joined:"02/03/2026", progress:64, submit:"Chưa nộp", grade:"—" },
    { name:"Bùi Thị Hương", joined:"03/03/2026", progress:45, submit:"Trễ hạn", grade:"—" },
    { name:"Trịnh Văn Long", joined:"03/03/2026", progress:80, submit:"Đã nộp", grade:"7.5" },
    { name:"Phan Thị Mai", joined:"04/03/2026", progress:70, submit:"Đã nộp", grade:"8.0" },
    { name:"Lý Văn Nam", joined:"04/03/2026", progress:55, submit:"Chưa nộp", grade:"—" },
    { name:"Cao Thị Oanh", joined:"05/03/2026", progress:96, submit:"Đã nộp", grade:"10" },
    { name:"Dương Văn Phúc", joined:"05/03/2026", progress:38, submit:"Trễ hạn", grade:"—" },
    { name:"Tạ Thị Quỳnh", joined:"06/03/2026", progress:82, submit:"Đã nộp", grade:"8.5" },
    { name:"Hồ Văn Sơn", joined:"06/03/2026", progress:60, submit:"Đã nộp", grade:"7.0" },
  ];

  // ---------- Submissions to grade (10) — Instructor ----------
  const submissions = [
    { id:"s1", student:"Hoàng Văn Em", assignment:"Bài tập 2: REST API CRUD", group:"Nhóm B1", submitted:"30/05/2026 14:20", status:"pending", file:"baitap2_em.zip" },
    { id:"s2", student:"Vũ Thị Phương", assignment:"Bài tập 2: REST API CRUD", group:"Nhóm B1", submitted:"30/05/2026 16:05", status:"pending", file:"api_phuong.zip" },
    { id:"s3", student:"Ngô Văn Khoa", assignment:"Bài tập 1: Component & Props", group:"Nhóm A1", submitted:"29/05/2026 09:12", status:"graded", score:"9.5", file:"component_khoa.zip" },
    { id:"s4", student:"Đặng Thu Hà", assignment:"Bài tập 1: Component & Props", group:"Nhóm A1", submitted:"29/05/2026 22:48", status:"pending", file:"props_ha.zip" },
    { id:"s5", student:"Phan Thị Mai", assignment:"Bài tập 3: JOIN nâng cao", group:"Nhóm F1", submitted:"28/05/2026 11:30", status:"graded", score:"8.0", file:"sql_mai.sql" },
    { id:"s6", student:"Lý Văn Nam", assignment:"Bài tập 3: JOIN nâng cao", group:"Nhóm F1", submitted:"31/05/2026 08:00", status:"pending", file:"join_nam.sql" },
    { id:"s7", student:"Cao Thị Oanh", assignment:"Bài tập 4: Dockerfile", group:"Nhóm E1", submitted:"27/05/2026 19:15", status:"graded", score:"10", file:"docker_oanh.zip" },
    { id:"s8", student:"Tạ Thị Quỳnh", assignment:"Bài tập 2: REST API CRUD", group:"Nhóm B2", submitted:"30/05/2026 13:40", status:"pending", file:"crud_quynh.zip" },
    { id:"s9", student:"Hồ Văn Sơn", assignment:"Bài tập 4: Dockerfile", group:"Nhóm E1", submitted:"31/05/2026 10:22", status:"pending", file:"docker_son.zip" },
    { id:"s10", student:"Dương Văn Phúc", assignment:"Bài tập 1: Component & Props", group:"Nhóm A2", submitted:"28/05/2026 23:55", status:"late", file:"comp_phuc.zip" },
  ];

  // ---------- Certificates (9) — Student ----------
  const certificates = [
    { id:"RE-2026-8892", course:"Java Spring Boot Microservices", instructor:"Lê Văn Cường", date:"15/05/2026", grade:"Xuất sắc" },
    { id:"RE-2026-7451", course:"Thiết kế CSDL & SQL Thực chiến", instructor:"Phạm Thị Dung", date:"02/05/2026", grade:"Giỏi" },
    { id:"RE-2026-5520", course:"UI/UX Design cho lập trình viên", instructor:"Hoàng Thu Trang", date:"20/04/2026", grade:"Giỏi" },
    { id:"RE-2026-4410", course:"Quản trị hệ thống Linux", instructor:"Đỗ Minh Quân", date:"08/04/2026", grade:"Khá" },
    { id:"RE-2026-3380", course:"TypeScript cho lập trình viên JS", instructor:"Nguyễn Văn An", date:"25/03/2026", grade:"Xuất sắc" },
    { id:"RE-2026-2199", course:"Git & GitHub toàn tập", instructor:"Vũ Đức Hải", date:"12/03/2026", grade:"Giỏi" },
    { id:"RE-2026-1087", course:"HTML & CSS hiện đại", instructor:"Hoàng Thu Trang", date:"28/02/2026", grade:"Xuất sắc" },
    { id:"RE-2025-9943", course:"Nhập môn lập trình Python", instructor:"Lê Văn Cường", date:"15/02/2026", grade:"Giỏi" },
    { id:"RE-2025-8821", course:"Tư duy thuật toán cơ bản", instructor:"Đỗ Minh Quân", date:"30/01/2026", grade:"Khá" },
  ];

  // ---------- Forum posts (8) — Student ----------
  const forum = [
    { id:"f1", title:"Sự khác nhau giữa useMemo và useCallback?", author:"Hoàng Văn Em", course:"ReactJS Nâng cao", replies:12, views:340, time:"1 giờ trước", pinned:true, tag:"Thảo luận" },
    { id:"f2", title:"Lỗi CORS khi gọi API từ React, cách xử lý?", author:"Vũ Thị Phương", course:"NodeJS & Express", replies:8, views:215, time:"3 giờ trước", pinned:true, tag:"Hỏi đáp" },
    { id:"f3", title:"Chia sẻ tài liệu ôn tập Spring Boot cuối kỳ", author:"Ngô Văn Khoa", course:"Spring Boot", replies:24, views:1203, time:"hôm qua", pinned:false, tag:"Tài liệu" },
    { id:"f4", title:"Khi nào nên dùng Index trong PostgreSQL?", author:"Đặng Thu Hà", course:"PostgreSQL", replies:6, views:178, time:"hôm qua", pinned:false, tag:"Hỏi đáp" },
    { id:"f5", title:"Best practice cấu trúc thư mục dự án Node?", author:"Phan Thị Mai", course:"NodeJS & Express", replies:15, views:432, time:"2 ngày trước", pinned:false, tag:"Thảo luận" },
    { id:"f6", title:"Docker container bị thoát ngay khi chạy?", author:"Lý Văn Nam", course:"Docker & Kubernetes", replies:9, views:267, time:"2 ngày trước", pinned:false, tag:"Hỏi đáp" },
    { id:"f7", title:"Cách viết Unit Test hiệu quả với Jest", author:"Cao Thị Oanh", course:"Kiểm thử tự động", replies:11, views:389, time:"3 ngày trước", pinned:false, tag:"Thảo luận" },
    { id:"f8", title:"Tổng hợp câu hỏi phỏng vấn ReactJS 2026", author:"Tạ Thị Quỳnh", course:"ReactJS Nâng cao", replies:31, views:2104, time:"4 ngày trước", pinned:false, tag:"Tài liệu" },
  ];

  // ---------- Curriculum (lecture player) ----------
  const curriculum = [
    { session:"Session 01 - Tổng quan về Agile & Scrum", open:true, lessons:[
      { t:"Tổng quan về quy trình phát triển phần mềm", dur:"9:23", done:true, active:true },
      { t:"Các mô hình phát triển phần mềm truyền thống", dur:"7:46", done:true },
      { t:"Giới thiệu về Agile Manifesto", dur:"5:59", done:true },
      { t:"Tổng quan về Scrum Framework", dur:"5:11", done:false },
      { t:"Các thành phần chính trong Scrum", dur:"8:21", done:false },
      { t:"Giới thiệu về Brainstorming", dur:"8:14", done:false },
    ]},
    { session:"Session 02 - Quy trình & vai trò trong Scrum", open:false, lessons:[
      { t:"Product Owner và Scrum Master", dur:"10:02", done:false },
      { t:"Development Team", dur:"6:30", done:false },
      { t:"Sprint Planning Meeting", dur:"7:18", done:false },
    ]},
    { session:"Session 03 - Lập kế hoạch Sprint", open:false, lessons:[
      { t:"User Story & Story Point", dur:"9:40", done:false },
      { t:"Product Backlog Refinement", dur:"8:05", done:false },
    ]},
  ];

  // ---------- Quiz questions ----------
  const quiz = {
    title:"Bài kiểm tra cuối khóa: Agile & Scrum",
    total:40, time:"25:40", proctored:true,
    question:{ no:5, text:"Đặc điểm nào sau đây là của mô hình Agile?", hint:"Chọn một đáp án đúng nhất.",
      options:[
        "Yêu cầu được xác định rõ ràng và không thay đổi từ đầu.",
        "Phát triển lặp đi lặp lại và tăng dần (Iterative and Incremental).",
        "Tập trung nhiều vào tài liệu hơn là phần mềm chạy được.",
        "Chỉ bắt đầu test sau khi toàn bộ code đã hoàn thành.",
      ], answer:1 },
    answered:[1,2,3,4], flagged:[7], current:5,
  };

  // ---------- Notifications ----------
  const notifications = [
    { icon:"clipboard", text:"Giảng viên đã tạo bài kiểm tra mới: Quiz chương 3", time:"10 phút trước", unread:true, color:"#2563eb" },
    { icon:"award", text:"Bạn đã nhận chứng chỉ khóa Java Spring Boot", time:"2 giờ trước", unread:true, color:"#10b981" },
    { icon:"message", text:'Phương đã trả lời bài viết của bạn trên diễn đàn', time:"5 giờ trước", unread:true, color:"#8b5cf6" },
    { icon:"warn", text:"Bài tập 'Truy vấn JOIN' sắp đến hạn nộp", time:"hôm qua", unread:false, color:"#f59e0b" },
    { icon:"check", text:"Bài tập 1 đã được chấm điểm: 9.5/10", time:"2 ngày trước", unread:false, color:"#10b981" },
  ];

  // ---------- Chart data ----------
  const charts = {
    traffic:[1200,1900,1500,2200,1800,2800,2400],
    trafficLabels:["T2","T3","T4","T5","T6","T7","CN"],
    newCourses:[45,60,55,80,65,90],
    courseLabels:["Th1","Th2","Th3","Th4","Th5","Th6"],
    completion:[62,68,71,74,78,82],
    revenue:[120,150,135,180,165,210,195,240],
  };

  // ---------- Question bank (per-course quiz questions) ----------
  const questionBank = [
    { id:"q1", course:"Lập trình ReactJS Nâng cao & Redux Toolkit", diff:"easy", q:"JSX là viết tắt của cụm từ nào?", a:["JavaScript XML","Java Source Extension","JSON Syntax","JavaScript Extra"], correct:0 },
    { id:"q2", course:"Lập trình ReactJS Nâng cao & Redux Toolkit", diff:"medium", q:"Hook nào dùng để ghi nhớ giá trị tính toán nặng?", a:["useState","useMemo","useEffect","useRef"], correct:1 },
    { id:"q3", course:"Lập trình ReactJS Nâng cao & Redux Toolkit", diff:"hard", q:"Trong Redux Toolkit, createAsyncThunk tự sinh ra mấy action?", a:["1","2","3","4"], correct:2 },
    { id:"q4", course:"Xây dựng RESTful API với NodeJS & Express", diff:"easy", q:"HTTP method nào dùng để tạo mới tài nguyên?", a:["GET","POST","DELETE","HEAD"], correct:1 },
    { id:"q5", course:"Xây dựng RESTful API với NodeJS & Express", diff:"medium", q:"Middleware trong Express được thực thi theo thứ tự nào?", a:["Ngẫu nhiên","Từ dưới lên","Theo thứ tự khai báo","Song song"], correct:2 },
    { id:"q6", course:"Java Spring Boot Microservices", diff:"hard", q:"Annotation nào đánh dấu một class là REST controller?", a:["@Service","@Component","@RestController","@Entity"], correct:2 },
    { id:"q7", course:"Java Spring Boot Microservices", diff:"easy", q:"Spring Boot dùng server nhúng mặc định nào?", a:["Tomcat","Nginx","Apache","IIS"], correct:0 },
    { id:"q8", course:"Thiết kế CSDL & SQL Thực chiến", diff:"medium", q:"Câu lệnh nào dùng để gộp bảng theo điều kiện?", a:["MERGE","JOIN","UNION","GROUP"], correct:1 },
    { id:"q9", course:"DevOps với Docker & Kubernetes", diff:"hard", q:"Lệnh nào build image từ Dockerfile?", a:["docker run","docker pull","docker build","docker exec"], correct:2 },
    { id:"q10", course:"DevOps với Docker & Kubernetes", diff:"easy", q:"Kubernetes quản lý đơn vị nhỏ nhất nào?", a:["Container","Pod","Node","Service"], correct:1 },
    { id:"q11", course:"Phát triển phần mềm với Agile / Scrum", diff:"medium", q:"Sprint trong Scrum thường kéo dài bao lâu?", a:["1 ngày","1-4 tuần","6 tháng","1 năm"], correct:1 },
    { id:"q12", course:"Phát triển phần mềm với Agile / Scrum", diff:"easy", q:"Ai chịu trách nhiệm về Product Backlog?", a:["Scrum Master","Product Owner","Dev Team","QA"], correct:1 },
  ];

  // ---------- Forum thread detail (body + comments) ----------
  const forumThread = {
    body: "Mình đang học phần Hooks và thấy có useMemo và useCallback. Cả hai đều nhận dependency array và đều dùng để tối ưu hiệu năng, nhưng mình chưa rõ khi nào nên dùng cái nào.\n\nTheo mình hiểu thì useMemo ghi nhớ một GIÁ TRỊ được tính toán, còn useCallback ghi nhớ một HÀM. Không biết mình hiểu vậy đã đúng chưa? Mọi người cho mình xin ví dụ thực tế với ạ. Cảm ơn cả nhà!",
    likes: 24, views: 340,
    comments: [
      { id:"cm1", author:"Nguyễn Văn An", role:"GV", time:"45 phút trước", likes:18, best:true,
        text:"Bạn hiểu đúng rồi đó! Phân biệt ngắn gọn:\n• useMemo(fn, deps) → ghi nhớ GIÁ TRỊ trả về của fn (dùng khi tính toán nặng).\n• useCallback(fn, deps) → ghi nhớ chính HÀM fn (dùng khi truyền callback xuống component con đã React.memo).\nThực ra useCallback(fn, deps) ≈ useMemo(() => fn, deps).",
        replies: [
          { id:"r1", author:"Hoàng Văn Em", time:"30 phút trước", likes:4, text:"Cảm ơn thầy ạ, vậy là useCallback chỉ là trường hợp đặc biệt của useMemo đúng không thầy?" },
          { id:"r2", author:"Nguyễn Văn An", role:"GV", time:"25 phút trước", likes:6, text:"Đúng vậy em. Về bản chất là thế, nhưng React tách riêng useCallback cho dễ đọc thôi." },
        ] },
      { id:"cm2", author:"Vũ Thị Phương", time:"1 giờ trước", likes:7, best:false,
        text:"Mình hay dùng useCallback khi truyền hàm xuống cho các component con bọc React.memo, không thì con bị re-render hoài. Còn useMemo thì mình dùng để lọc/sắp xếp một mảng lớn.",
        replies: [] },
      { id:"cm3", author:"Ngô Văn Khoa", time:"2 giờ trước", likes:3, best:false,
        text:"Lưu ý là đừng lạm dụng nhé mọi người, bản thân useMemo/useCallback cũng tốn chi phí. Chỉ tối ưu khi thật sự cần thiết thôi.",
        replies: [] },
    ],
  };

  window.DATA = { courses, users, roles, approvals, activity, assignments, groups, groupStudents,
    submissions, certificates, forum, forumThread, curriculum, quiz, notifications, charts, questionBank, T };
  window.UI = { avatarColor, initials, AV };
})();
