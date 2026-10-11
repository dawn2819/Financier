# Đánh giá và lộ trình Financier

## Hiện trạng

Financier là ứng dụng Android Kotlin dùng kiến trúc MVVM, Room/SQLCipher cho dữ liệu cục bộ và các màn hình giao dịch, ngân sách, báo cáo, tài khoản, quét VietQR và chat Gemini. Phạm vi hiện tại phù hợp làm ứng dụng quản lý tài chính cá nhân offline-first; phần ngân hàng hiện đọc thông báo SMS, chưa có kết nối ngân hàng thực.

### Các điểm cần xử lý

- Một số màn hình có TODO hoặc xử lý bất đồng bộ chưa kín. Bộ chọn Dashboard hiện lọc số dư, thu/chi, xu hướng và giao dịch gần đây theo tài khoản hoặc tất cả tài khoản; lựa chọn được lưu để dùng làm tài khoản mặc định cho giao dịch mới.
- Màn hình Ngân sách hiện có dự báo tổng chi tiêu cuối tháng cho tháng hiện tại, dựa trên mức chi trung bình mỗi ngày; tháng đã qua hoặc tương lai sẽ không hiển thị dự báo.
- Trợ lý AI hiện nhận số dư tổng hợp từ số dư đầu kỳ và toàn bộ giao dịch, tổng thu/chi tháng đã quy đổi về tiền tệ cài đặt, cùng ba danh mục chi lớn nhất. Lịch sử hội thoại được giữ theo cặp câu hỏi/câu trả lời và giới hạn độ dài.
- Danh sách giao dịch hỗ trợ lọc theo tài khoản, tháng hiện tại và 30 ngày gần nhất kết hợp với tìm kiếm và lọc loại thu/chi.
- Nhận diện SMS cần thận trọng: bộ lọc người gửi trước đây khớp cả chữ viết tắt nằm giữa chuỗi, gây nguy cơ nhận nhầm SMS không phải ngân hàng. Đã đổi sang so khớp mã/tên ngân hàng theo ranh giới ký tự.
- Build chưa được xác nhận trên môi trường hiện tại vì Gradle không tìm thấy JDK (`JAVA_HOME` chưa thiết lập và không có `java` trên `PATH`).
- Cấu hình Room đang dùng `fallbackToDestructiveMigration()`: đổi schema có thể xóa dữ liệu người dùng. Trước khi phát hành cần có migration tường minh và xuất/lưu schema.
- Tỷ giá USD/VND đang là hằng số trong `CurrencyFormatter`; số dư quy đổi có thể sai theo thời gian. Nên lưu tỷ giá hoặc thời điểm cập nhật và tránh trình bày quy đổi ước tính như số dư chính xác.
- Đăng nhập, giao dịch SMS và dữ liệu đều cục bộ. Mật khẩu mặc định/seed mẫu chỉ phù hợp demo; cần bỏ thông tin đăng nhập mặc định trước khi phát hành.
- Có thông tin nhạy cảm thẻ (`cardPin`) trong entity tài khoản. Không nên lưu PIN thẻ; cần loại bỏ trường này qua migration và làm sạch dữ liệu cũ.

## Lộ trình đề xuất

### Ưu tiên 1 — Độ tin cậy dữ liệu

1. Thêm migration Room theo từng phiên bản, bật schema export và có quy trình backup/restore mã hóa.
2. Dùng `BigDecimal` hoặc số nguyên đơn vị tiền nhỏ nhất thay cho `Double` trong tiền tệ; quy định làm tròn khi đổi tiền.
3. Đồng nhất tính khoảng ngày theo múi giờ thiết bị; thêm giao dịch lặp lại và phân loại giao dịch hoàn/đảo.
4. Thêm kiểm tra ràng buộc ở repository: số tiền dương, tài khoản thuộc người dùng, loại giao dịch hợp lệ và không cho sửa giao dịch của người khác.
5. Làm luồng nhập SMS có trạng thái chờ xác nhận, xem nội dung đã che dữ liệu, chống trùng ổn định và bật/tắt quyền đồng bộ rõ ràng.

### Ưu tiên 2 — Tính năng tài chính hữu ích

1. Ngân sách linh hoạt theo tuần/tháng, chuyển ngân sách chưa dùng, cảnh báo theo ngưỡng và dự báo cuối kỳ.
2. Giao dịch định kỳ, lịch hóa đơn, nhắc thanh toán và phát hiện khoản chi định kỳ mới.
3. Mục tiêu tiết kiệm với số dư mục tiêu, tiến độ, thời hạn và đóng góp theo từng tài khoản.
4. Tìm kiếm nâng cao theo khoảng ngày, tài khoản, loại, danh mục, số tiền và trạng thái SMS; xuất/nhập CSV có xác nhận và xử lý lỗi từng dòng.
5. Báo cáo dòng tiền, so sánh kỳ trước, xu hướng chi tiêu và biểu đồ theo tài khoản/tiền tệ.

### Ưu tiên 3 — Lập trình và phân tích nâng cao

1. Chuyển dần từ ViewModel tự truy cập DAO sang Repository/UseCase để kiểm thử quy tắc nghiệp vụ độc lập giao diện.
2. Tách mô hình tiền (`Money` gồm amount và currency), dịch vụ quy đổi có nguồn/timestamp, và quy tắc báo cáo dùng chung.
3. Phân tích bất thường cục bộ: giao dịch lớn hơn mức thông thường, tăng chi theo danh mục, hóa đơn gần đến hạn; giải thích bằng số liệu truy nguyên được.
4. Cho AI nhận dữ liệu tổng hợp tối thiểu, xin đồng ý trước khi gửi ra mạng, không gửi ghi chú/tài khoản định danh mặc định; hiển thị nguồn và kỳ dữ liệu trong câu trả lời.
5. Bổ sung bộ kiểm thử nghiệp vụ cho tính số dư, giới hạn ngân sách, quy đổi tiền, lọc ngày và parser SMS; kiểm thử migration trên bản DB cũ.

## Trình tự khuyến nghị

Hoàn thiện migration và bảo vệ dữ liệu nhạy cảm trước; kế đến chuẩn hóa kiểu tiền và quy tắc ngày; sau đó mới phát triển ngân sách dự báo, giao dịch định kỳ và phân tích AI. Trình tự này giảm rủi ro làm sai báo cáo khi dữ liệu và phép tính nền chưa ổn định.
