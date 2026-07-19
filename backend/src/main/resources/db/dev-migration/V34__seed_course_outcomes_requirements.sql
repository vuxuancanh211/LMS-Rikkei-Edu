-- Bổ sung "Bạn sẽ học được gì" (learning_outcomes) và "Yêu cầu" (requirements) cho các khóa học
-- seed/QA đã có sẵn trong DB, để trang tổng quan khóa học (StuCourseDetail/InsCourseDetail) có
-- đầy đủ dữ liệu xem thử thay vì rỗng. Chỉ set khi cột đang NULL/rỗng — không đè lên dữ liệu
-- giảng viên đã tự nhập qua UI.

UPDATE courses SET learning_outcomes = '["Xây dựng REST API hoàn chỉnh với Spring Boot","Kết nối và thao tác dữ liệu với Spring Data JPA","Xác thực và phân quyền với Spring Security","Triển khai ứng dụng Spring Boot lên môi trường thực tế"]',
  requirements = '["Kiến thức cơ bản về Java (OOP, collection)","Đã từng làm việc với SQL cơ bản","Máy tính cài sẵn JDK 17+ và IDE (IntelliJ/Eclipse)"]'
WHERE id = '20000000-0000-0000-0000-000000000001' AND (learning_outcomes IS NULL OR learning_outcomes = '');

UPDATE courses SET learning_outcomes = '["Xây dựng giao diện người dùng với ReactJS (component, props, state)","Quản lý state hiệu quả với Hooks (useState, useEffect, useContext)","Thiết kế giao diện responsive nhanh với TailwindCSS","Kết nối API và hiển thị dữ liệu động"]',
  requirements = '["Kiến thức cơ bản về HTML, CSS, JavaScript","Không yêu cầu kinh nghiệm React trước đó"]'
WHERE id = '20000000-0000-0000-0000-000000000003' AND (learning_outcomes IS NULL OR learning_outcomes = '');

UPDATE courses SET learning_outcomes = '["Đóng gói ứng dụng bằng Docker (image, container, volume)","Viết Dockerfile và docker-compose cho ứng dụng nhiều service","Hiểu kiến trúc cơ bản của Kubernetes (pod, deployment, service)","Triển khai và scale ứng dụng trên cụm Kubernetes đơn giản"]',
  requirements = '["Kiến thức cơ bản về Linux command line","Đã từng xây dựng ít nhất 1 ứng dụng web đơn giản"]'
WHERE id = '20000000-0000-0000-0000-000000000004' AND (learning_outcomes IS NULL OR learning_outcomes = '');

UPDATE courses SET learning_outcomes = '["Nhận diện và áp dụng các Design Pattern phổ biến (Singleton, Factory, Observer, Strategy...)","Tổ chức code hướng đối tượng dễ bảo trì và mở rộng","Đọc hiểu UML class diagram cơ bản","Áp dụng pattern phù hợp vào bài toán thực tế"]',
  requirements = '["Nắm vững kiến thức OOP cơ bản (lớp, kế thừa, đa hình)","Biết ít nhất 1 ngôn ngữ hướng đối tượng (Java/C#/Python)"]'
WHERE id = '2f5c38dd-704f-451f-b1fc-bceac5ea790b' AND (learning_outcomes IS NULL OR learning_outcomes = '');

UPDATE courses SET learning_outcomes = '["Hiểu kiến trúc RAG (Retrieval-Augmented Generation) và khi nào nên dùng","Xây dựng pipeline truy vấn tài liệu với LangChain","Tích hợp OpenAI API để sinh câu trả lời từ ngữ cảnh","Đánh giá và tối ưu chất lượng câu trả lời của hệ thống RAG"]',
  requirements = '["Thành thạo Python cơ bản","Hiểu khái niệm cơ bản về LLM/embedding","Có tài khoản OpenAI API để thực hành"]'
WHERE id = '20000000-0000-0000-0000-000000000005' AND (learning_outcomes IS NULL OR learning_outcomes = '');

UPDATE courses SET learning_outcomes = '["Thiết kế hệ thống theo kiến trúc microservices với Spring Boot","Giao tiếp giữa các service qua REST/message queue","Áp dụng service discovery và API Gateway","Xử lý transaction phân tán và resilience (circuit breaker, retry)"]',
  requirements = '["Đã quen thuộc với Spring Boot cơ bản","Hiểu khái niệm REST API và Docker cơ bản"]'
WHERE id = '5385d728-b793-4195-803a-eca19332e8e1' AND (learning_outcomes IS NULL OR learning_outcomes = '');

UPDATE courses SET learning_outcomes = '["Thiết kế hệ thống theo kiến trúc microservices với Spring Boot","Giao tiếp giữa các service qua REST/message queue","Áp dụng service discovery và API Gateway","Xử lý transaction phân tán và resilience (circuit breaker, retry)"]',
  requirements = '["Đã quen thuộc với Spring Boot cơ bản","Hiểu khái niệm REST API và Docker cơ bản"]'
WHERE id = '6d1aa295-46ec-438a-82e4-9fb39c52f329' AND (learning_outcomes IS NULL OR learning_outcomes = '');

UPDATE courses SET learning_outcomes = '["Nắm vững cú pháp Java cơ bản (biến, kiểu dữ liệu, vòng lặp, điều kiện)","Viết class, method và làm việc với mảng/collection đơn giản","Xử lý ngoại lệ cơ bản trong Java","Xây dựng chương trình console hoàn chỉnh đầu tiên"]',
  requirements = '["Không yêu cầu kiến thức lập trình trước đó","Máy tính cài sẵn JDK và IDE bất kỳ"]'
WHERE id = '90a0f218-96e0-427b-9bba-97b0fd1e563f' AND (learning_outcomes IS NULL OR learning_outcomes = '');

UPDATE courses SET learning_outcomes = '["Sử dụng Git để quản lý phiên bản mã nguồn (commit, branch, merge)","Xử lý xung đột (conflict) khi làm việc nhóm","Sử dụng GitHub để lưu trữ và cộng tác trên dự án","Áp dụng quy trình Git flow cơ bản trong dự án thực tế"]',
  requirements = '["Không yêu cầu kinh nghiệm lập trình trước đó","Máy tính cài sẵn Git và có tài khoản GitHub"]'
WHERE id = 'e8e4e5db-d2aa-46ed-bd3c-b5c4d2182622' AND (learning_outcomes IS NULL OR learning_outcomes = '');

UPDATE courses SET learning_outcomes = '["Viết truy vấn SQL nâng cao (window function, CTE, subquery)","Thiết kế index tối ưu hiệu năng truy vấn","Phân tích query plan với EXPLAIN ANALYZE","Tối ưu hiệu năng cơ sở dữ liệu cho hệ thống có lượng dữ liệu lớn"]',
  requirements = '["Đã nắm vững SQL cơ bản (SELECT, JOIN, GROUP BY)","Có kinh nghiệm làm việc với PostgreSQL hoặc hệ CSDL quan hệ khác"]'
WHERE id = '20000000-0000-0000-0000-000000000002' AND (learning_outcomes IS NULL OR learning_outcomes = '');

UPDATE courses SET learning_outcomes = '["Hiểu cấu trúc thẻ HTML cơ bản và ngữ nghĩa (semantic HTML)","Xây dựng layout trang web bằng CSS (box model, flexbox)","Tạo trang web tĩnh hoàn chỉnh đầu tiên","Làm quen với quy trình responsive design cơ bản"]',
  requirements = '["Không yêu cầu kiến thức lập trình trước đó","Máy tính có trình duyệt web và trình soạn thảo code (VS Code)"]'
WHERE id = '60f3a98a-e216-4849-b72c-1e2edab7fd50' AND (learning_outcomes IS NULL OR learning_outcomes = '');

UPDATE courses SET learning_outcomes = '["Nắm rõ quy trình soạn thảo và xuất bản khóa học trên hệ thống","Sử dụng thành thạo trình dựng nội dung (chương, bài giảng, tài liệu)","Hiểu quy trình duyệt/từ chối và cách xử lý phản hồi từ admin","Áp dụng best practice khi trình bày nội dung giảng dạy trực tuyến"]',
  requirements = '["Đã có tài khoản giảng viên trên hệ thống","Sẵn sàng tài liệu/giáo án cho khóa học đầu tiên"]'
WHERE id = '6bbb8eec-294b-41dc-8f68-7614a251d404' AND (learning_outcomes IS NULL OR learning_outcomes = '');

UPDATE courses SET learning_outcomes = '["Hiểu vòng đời trạng thái của một khóa học (Nháp → Chờ duyệt → Xuất bản)","Sử dụng hệ thống phiên bản (version) để quản lý thay đổi nội dung","Xử lý tình huống bị từ chối duyệt và chỉnh sửa lại đúng quy trình","Phối hợp với admin để khóa học được duyệt nhanh chóng"]',
  requirements = '["Đã tạo ít nhất 1 khóa học nháp trên hệ thống"]'
WHERE id = '15822b17-1f7a-45c0-85ad-3d3b22a0962a' AND (learning_outcomes IS NULL OR learning_outcomes = '');

UPDATE courses SET learning_outcomes = '["Chuẩn bị và trình bày phần giới thiệu bản thân (self-introduction) ấn tượng","Trả lời tự tin các câu hỏi kỹ thuật thường gặp (data structure, OOP, hệ thống)","Xử lý bài tập coding trực tiếp (live coding) trong phỏng vấn","Đàm phán lương và xử lý câu hỏi tình huống (behavioral question)"]',
  requirements = '["Có kiến thức lập trình nền tảng (ít nhất 1 ngôn ngữ)","Đã từng làm qua ít nhất vài bài tập thuật toán cơ bản"]'
WHERE id = '587ef4d5-9f85-4bf0-93ec-92e90a6fe029' AND (learning_outcomes IS NULL OR learning_outcomes = '');

UPDATE courses SET learning_outcomes = '["Phân biệt rõ ưu/nhược điểm giữa kiến trúc Monolith và Microservices","Xác định thời điểm phù hợp để tách một hệ thống thành microservices","Thiết kế ranh giới service (service boundary) hợp lý","Đánh giá đánh đổi (trade-off) về vận hành, chi phí và độ phức tạp"]',
  requirements = '["Đã có kinh nghiệm xây dựng ít nhất 1 ứng dụng backend hoàn chỉnh","Hiểu khái niệm cơ bản về REST API và cơ sở dữ liệu"]'
WHERE id = '64726594-c64c-4076-9c98-52948754559c' AND (learning_outcomes IS NULL OR learning_outcomes = '');

UPDATE courses SET learning_outcomes = '["Nhận diện các lỗ hổng bảo mật phổ biến (OWASP Top 10)","Áp dụng thực hành lập trình an toàn (input validation, mã hóa mật khẩu)","Phòng chống SQL Injection, XSS và CSRF trong ứng dụng web","Xây dựng thói quen tư duy bảo mật khi thiết kế hệ thống"]',
  requirements = '["Đã biết lập trình web cơ bản (bất kỳ ngôn ngữ nào)","Không yêu cầu kiến thức bảo mật trước đó"]'
WHERE id = 'c1989ab8-f161-4c6f-a106-2b7c4f88ad18' AND (learning_outcomes IS NULL OR learning_outcomes = '');

UPDATE courses SET learning_outcomes = '["Nắm vững 4 tính chất cốt lõi của OOP: đóng gói, kế thừa, đa hình, trừu tượng","Áp dụng 5 nguyên tắc thiết kế SOLID vào code Java thực tế","Quản lý ngoại lệ và bộ nhớ đúng cách trong ứng dụng hướng đối tượng","Refactor code cũ theo hướng dễ bảo trì và mở rộng hơn"]',
  requirements = '["Đã biết cú pháp Java cơ bản (biến, vòng lặp, hàm)","Không yêu cầu kinh nghiệm OOP trước đó"]'
WHERE id = '4e44882d-fb2d-4e1c-91dc-87a80f0a83cc' AND (learning_outcomes IS NULL OR learning_outcomes = '');

UPDATE courses SET learning_outcomes = '["Nắm vững CSS selector, box model và các đơn vị đo lường","Xây dựng layout linh hoạt với Flexbox và Grid","Thiết kế giao diện responsive trên nhiều kích thước màn hình","Áp dụng các kỹ thuật CSS nâng cao thường gặp khi phỏng vấn"]',
  requirements = '["Đã biết HTML cơ bản","Máy tính có trình duyệt và trình soạn thảo code"]'
WHERE id = '67407332-b3c5-4f37-8559-d39404b0a5ef' AND (learning_outcomes IS NULL OR learning_outcomes = '');

UPDATE courses SET learning_outcomes = '["Hiểu vai trò của kiểm thử phần mềm trong vòng đời phát triển","Viết test case và thực hiện kiểm thử thủ công (manual testing)","Phân biệt các loại kiểm thử: unit, integration, regression","Báo cáo lỗi (bug report) rõ ràng, đầy đủ thông tin cho lập trình viên"]',
  requirements = '["Không yêu cầu kiến thức lập trình trước đó","Có tư duy logic và cẩn thận với chi tiết"]'
WHERE id = '6a16e250-fcac-4a0c-b5ea-3b033e4656fd' AND (learning_outcomes IS NULL OR learning_outcomes = '');

UPDATE courses SET learning_outcomes = '["Nắm vững cú pháp TypeScript cơ bản (type, interface, generic)","Chuyển đổi code JavaScript sang TypeScript an toàn kiểu dữ liệu","Cấu hình dự án TypeScript với tsconfig","Áp dụng TypeScript vào dự án React/Node.js thực tế"]',
  requirements = '["Đã thành thạo JavaScript cơ bản (ES6+)","Có Node.js cài sẵn trên máy để thực hành"]'
WHERE id = 'ac37af0b-110e-4e99-ad11-190a7adb1ed4' AND (learning_outcomes IS NULL OR learning_outcomes = '');

UPDATE courses SET learning_outcomes = '["Củng cố kiến thức OOP nền tảng: lớp, đối tượng, kế thừa, đa hình","Ôn tập cấu trúc dữ liệu cơ bản: mảng, danh sách liên kết, stack, queue","Luyện tập bài tập tổng hợp kết hợp OOP và cấu trúc dữ liệu","Chuẩn bị nền tảng vững chắc trước khi học các khóa nâng cao"]',
  requirements = '["Đã học qua ít nhất 1 khóa lập trình nhập môn","Muốn củng cố lại kiến thức nền tảng trước khi học tiếp"]'
WHERE id = '2c2e4c38-23dc-4c70-9db9-44b79f8c8a5f' AND (learning_outcomes IS NULL OR learning_outcomes = '');

UPDATE courses SET learning_outcomes = '["Nắm vững lộ trình học lập trình từ cơ bản đến frontend thực chiến","Xây dựng nền tảng HTML/CSS/JavaScript vững chắc","Làm quen với quy trình làm việc thực tế của một lập trình viên frontend","Hoàn thành dự án thực tập tổng hợp cuối chương trình"]',
  requirements = '["Không yêu cầu kiến thức lập trình trước đó","Cam kết thời gian học tập xuyên suốt chương trình thực tập"]'
WHERE id = '61751581-cacc-43c5-8e89-58d96de5a86c' AND (learning_outcomes IS NULL OR learning_outcomes = '');

UPDATE courses SET learning_outcomes = '["Hiểu cơ chế lưu trữ bộ nhớ và cách dữ liệu được tổ chức","Cài đặt và sử dụng thành thạo mảng, danh sách liên kết, stack, queue","Làm việc với bảng băm, cây (tree) và heap","Áp dụng thuật toán duyệt đồ thị BFS/DFS vào bài toán thực tế"]',
  requirements = '["Đã biết lập trình cơ bản (biến, vòng lặp, hàm)","Phù hợp cho người chuẩn bị phỏng vấn kỹ thuật"]'
WHERE id = '669edabb-a426-4247-ad75-2d8d168bc0a6' AND (learning_outcomes IS NULL OR learning_outcomes = '');

UPDATE courses SET learning_outcomes = '["Nắm được kiến thức nền tảng về HTML, CSS và JavaScript","Xây dựng trang web tĩnh có tương tác cơ bản bằng JavaScript","Hiểu cách 3 công nghệ HTML/CSS/JS phối hợp với nhau","Làm quen với công cụ phát triển web cơ bản (DevTools)"]',
  requirements = '["Không yêu cầu kiến thức lập trình trước đó"]'
WHERE id = '407e0320-08ab-4e6e-b1f6-9957b0c4f88a' AND (learning_outcomes IS NULL OR learning_outcomes = '');

UPDATE courses SET learning_outcomes = '["Nắm vững cú pháp thẻ và thuộc tính HTML/HTML5","Xây dựng cấu trúc tài liệu chuẩn ngữ nghĩa (Semantic Elements)","Tích hợp CSS và JavaScript vào trang HTML","Xây dựng form nhập liệu và xử lý sự kiện cơ bản"]',
  requirements = '["Không yêu cầu kiến thức lập trình trước đó","Máy tính có trình duyệt web và trình soạn thảo code"]'
WHERE id = '513ccfd5-8642-4286-b1ba-b85e6bc1a72f' AND (learning_outcomes IS NULL OR learning_outcomes = '');
