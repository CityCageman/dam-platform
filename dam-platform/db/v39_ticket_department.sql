-- v39: 工单补 department，用于角色数据范围 DEPARTMENT/SELF 的后端过滤
BEGIN;
ALTER TABLE dam_ticket.ticket_order
    ADD COLUMN IF NOT EXISTS department varchar(100);

COMMENT ON COLUMN dam_ticket.ticket_order.department
    IS '申请部门；用于角色数据范围过滤，存量工单为空时由申请用户补录';

CREATE INDEX IF NOT EXISTS idx_ticket_order_department
    ON dam_ticket.ticket_order (department);
COMMIT;
