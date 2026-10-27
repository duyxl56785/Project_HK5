import type { DemoProduct, DemoState, ProductArt } from './demoTypes';
import type { ProductStatus } from '../features/product/productManagementTypes';

const categoryId = (n: number) => `10000000-0000-4000-8000-${String(n).padStart(12, '0')}`;
const sellerId = (n: number) => `20000000-0000-4000-8000-${String(n).padStart(12, '0')}`;
const storeId = (n: number) => `30000000-0000-4000-8000-${String(n).padStart(12, '0')}`;
type Seed = readonly [string, string, number, ProductArt, string, ProductStatus?];
const seeds: readonly Seed[] = [
  ['Bình gốm men cát', '285000', 2, 'vase', 'Dáng bình mềm, lớp men cát mộc mạc và miệng nhỏ vừa một cành hoa. Một điểm lặng ấm áp trên bàn gỗ hoặc kệ sách.'],
  ['Cốc gốm buổi sớm', '145000', 3, 'cup', 'Chiếc cốc men kem với quai cầm tròn vừa tay, dành cho cà phê đầu ngày hay một tách trà chiều chậm rãi.'],
  ['Đèn bàn An', '690000', 5, 'lamp', 'Chụp đèn vải lanh dịu mắt trên chân gỗ thanh mảnh. Ánh sáng ấm làm góc đọc sách nhỏ trở nên gần gũi hơn.'],
  ['Túi vải hằng ngày', '195000', 6, 'bag', 'Túi vải dày màu tự nhiên, đường may gọn và quai đeo rộng. Đủ chỗ cho cuốn sách yêu thích cùng những món đồ nhỏ.'],
  ['Khăn linen màu yến mạch', '320000', 6, 'linen', 'Bề mặt linen thoáng nhẹ, sắc yến mạch dễ phối với bàn ăn và đồ gốm. Nếp vải tự nhiên mang nét đẹp bình dị.'],
  ['Chậu xanh bên cửa', '215000', 5, 'plant', 'Một mảng xanh nhỏ trong chiếc chậu tối giản, vừa vặn đặt bên cửa sổ. Dành cho những góc nhà cần thêm sức sống.'],
  ['Bình gốm dáng trăng', '420000', 2, 'vase', 'Thân bình tròn với sắc men đất trầm, đẹp khi cắm hoa khô hoặc đứng một mình. Mỗi góc nhìn đều có một nét riêng.'],
  ['Cốc men sương', '165000', 3, 'cup', 'Mẫu cốc mới với sắc men xanh sương nhẹ và lòng cốc rộng. Đang hoàn thiện thông tin trước khi gửi kiểm duyệt.', 'DRAFT'],
  ['Đèn góc hiên', '850000', 5, 'lamp', 'Thiết kế đèn thanh thoát lấy cảm hứng từ hiên nhà, phối gỗ sáng và vải dệt. Mẫu đang chờ đội ngũ kiểm duyệt.', 'PENDING_REVIEW'],
  ['Túi dạo cuối tuần', '235000', 6, 'bag', 'Phom túi rộng rãi với đáy phẳng và tông xanh lá nhạt. Thông tin chất liệu đang được bổ sung để gửi duyệt lại.', 'REJECTED'],
  ['Khăn bàn mùa nắng', '380000', 6, 'linen', 'Khăn bàn dệt thô có sắc vàng nhạt và viền may đơn giản. Sản phẩm đang tạm khóa để rà soát nội dung mô tả.', 'SUSPENDED'],
  ['Chậu gốm mầm non', '175000', 5, 'plant', 'Chậu gốm nhỏ màu đất sáng với dáng thuôn thanh lịch. Mẫu đã ngừng bán và có thể được chủ tiệm chỉnh sửa lại.', 'INACTIVE'],
];

export function createDemoFixtures(): DemoState {
  const products: DemoProduct[] = seeds.map(([name, unitPrice, category, art, description, status = 'ACTIVE'], index) => ({
    id: `40000000-0000-4000-8000-${String(index + 1).padStart(12, '0')}`,
    storeId: storeId(index === 7 || index === 9 || index === 11 ? 1 : index % 2 + 1),
    categoryId: categoryId(category), sku: `NEP-DEMO-${String(index + 1).padStart(3, '0')}`,
    name, unitPrice, currency: 'VND', status, version: '0', description, art, images: [],
    reviewReason: status === 'REJECTED' ? 'Vui lòng bổ sung thông tin chất liệu.' : status === 'SUSPENDED' ? 'Cần rà soát thông tin mô tả.' : null,
  }));
  return {
    revision: 0,
    categories: [
      { id: categoryId(1), name: 'Gốm & bàn ăn', parentId: null },
      { id: categoryId(2), name: 'Bình & lọ', parentId: categoryId(1) },
      { id: categoryId(3), name: 'Cốc & tách', parentId: categoryId(1) },
      { id: categoryId(4), name: 'Không gian sống', parentId: null },
      { id: categoryId(5), name: 'Đèn & cây', parentId: categoryId(4) },
      { id: categoryId(6), name: 'Vải & túi', parentId: categoryId(4) },
    ],
    sellers: [
      { id: sellerId(1), accountId: 'seller-a', version: '0', status: 'APPROVED', reviewReason: null },
      { id: sellerId(2), accountId: 'seller-b', version: '0', status: 'APPROVED', reviewReason: null },
    ],
    stores: [
      { id: storeId(1), sellerId: sellerId(1), name: 'Nếp Nhà', slug: 'nep-nha', open: true, version: '0' },
      { id: storeId(2), sellerId: sellerId(2), name: 'Mộc Studio', slug: 'moc-studio', open: true, version: '0' },
    ],
    products,
  };
}
